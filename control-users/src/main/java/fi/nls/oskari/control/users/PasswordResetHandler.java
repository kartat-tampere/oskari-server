package fi.nls.oskari.control.users;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import fi.nls.oskari.annotation.OskariActionRoute;
import fi.nls.oskari.control.*;
import fi.nls.oskari.control.users.model.Email;
import fi.nls.oskari.control.users.service.UserRegistrationService;
import fi.nls.oskari.control.users.service.MailSenderService;
import fi.nls.oskari.domain.User;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.service.OskariComponentManager;
import fi.nls.oskari.service.ServiceException;
import fi.nls.oskari.service.UserService;
import fi.nls.oskari.user.IbatisRoleService;
import fi.nls.oskari.user.IbatisUserService;
import fi.nls.oskari.util.PropertyUtil;

@OskariActionRoute("UserPasswordReset")
public class PasswordResetHandler extends RestActionHandler {

    private static final Logger log = LogFactory.getLogger(PasswordResetHandler.class);

    private static final String PARAM_UUID = "uuid";
    private static final String PARAM_EMAIL = "email";
    private static final String PARAM_SET_PASSWORD = "setPassword";
    private static final String PARAM_PASSWORD = "password";

    private static final String ROLE_USER = "User";
    private ObjectMapper mapper = new ObjectMapper();

    private UserRegistrationService registerTokenService = null;
    private final MailSenderService mailSenderService = new MailSenderService();
    private final IbatisUserService ibatisUserService = new IbatisUserService();
    private UserService userService;

    @Override
    public void init() {
        try {
            userService = UserService.getInstance();
        } catch (ServiceException se) {
            log.error(se, "Unable to initialize User service!");
        }
        registerTokenService = OskariComponentManager.getComponentOfType(UserRegistrationService.class);
    }

    @Override
    public void handlePost(ActionParameters params) throws ActionException {
        if(!PropertyUtil.getOptional("allow.registration", false)) {
            throw new ActionDeniedException("Registration disabled");
        }
        final String requestEmail = params.getHttpParam(PARAM_EMAIL, "");
        if (!requestEmail.isEmpty()) {
            handlePasswordReset(requestEmail, RegistrationUtil.getServerAddress(params));

        } else if (params.getHttpParam(PARAM_SET_PASSWORD) != null) {
            final Email token = parseContentForEmailUpdate(params);
            updatePassword(token);
        } else {
            throw new ActionException("Request must contain either " + PARAM_EMAIL + " or " + PARAM_SET_PASSWORD + ".");
        }
    }

    public void handlePasswordReset(final String email, final String serverAddress) throws ActionException {

        if (!isUsernameExistsForLogin(email)) {
            throw new ActionDeniedException("Username for login doesn't exist for email address: " + email);
        }
        String uuid = UUID.randomUUID().toString();
        Email emailToken = new Email();
        emailToken.setEmail(email);
        emailToken.setUuid(uuid);
        emailToken.setExpiryTimestamp(RegistrationUtil.createExpiryTime());
        registerTokenService.addEmail(emailToken);

        String username = registerTokenService.findUsernameForEmail(email);
        User user = ibatisUserService.findByUserName(username);
        mailSenderService.sendEmailForResetPassword(user, uuid, serverAddress);
    }

    public Email parseContentForEmailUpdate(ActionParameters params) throws ActionException {

        Email token = new Email();
        Map<String, String> jsonObjectMap = readJsonFromStream(params.getRequest());

        // JSON object ONLY need to have 2 attributes: 'uuid' and 'password'
        if (jsonObjectMap.size() != 2) {
            throw new ActionParamsException("JSON object MUST contain only 2 attributes: 'uuid' and 'password'");
        }
        token.setPassword(jsonObjectMap.get(PARAM_PASSWORD));
        token.setUuid(jsonObjectMap.get(PARAM_UUID));

        // validate
        if (token.getPassword() == null || token.getUuid() == null) {
            throw new ActionParamsException("JSON object MUST contain only 2 attributes: 'uuid' and 'password'");
        }
        Email tempEmail = registerTokenService.findByToken(token.getUuid());
        if (tempEmail == null) {
            throw new ActionParamsException("UUID not found.");
        }
        if (new Date().after(tempEmail.getExpiryTimestamp())) {
            // TODO: use redis to save token with expiry so we don't need to care about expiration
            registerTokenService.deleteEmailToken(token.getUuid());
            throw new ActionDeniedException("UUID expired.");
        }
        token.setEmail(tempEmail.getEmail());
        return token;
    }

    public void updatePassword(Email token) throws ActionException {

        String username = registerTokenService.findUsernameForEmail(token.getEmail());

        if (username == null) {
            throw new ActionParamsException("Username doesn't exist.");
        }
        String loginPassword = ibatisUserService.getPassword(username);
        try {
            if (loginPassword != null && !loginPassword.isEmpty()) {
                userService.updateUserPassword(username, token.getPassword());
            } else {
                // Create entry in oskari_jaas_user table
                // TODO: check that we want to allow this
                userService.setUserPassword(username, token.getPassword());

                // Create link between User and Role (oskari_role_oskari_user); For logged user's default view.
                // TODO: the role should have been added in some previous step - remove it from here
                User user = userService.getUser(username);
                int roleId = registerTokenService.findUserRoleId(ROLE_USER);
                IbatisRoleService roleService = new IbatisRoleService();
                roleService.linkRoleToNewUser(roleId, user.getId());
            }
            // After password updated/created, delete the entry related to token from database
            registerTokenService.deleteEmailToken(token.getUuid());
        } catch (ServiceException se) {
            throw new ActionException(se.getMessage(), se);
        }
    }

    /**
     * Checks if the username exists for login, for the email Address being sent.
     * 
     * @param emailAddress
     * @return
     * @throws ActionException
     */
    private final boolean isUsernameExistsForLogin(final String emailAddress) throws ActionException {
        // Retrieves username , if exists in oskari_users table.
        String username = registerTokenService.findUsernameForEmail(emailAddress);
        if (username == null) {
            return false;
        }

        // Retrieves username for login, if exists in oskari_jaas_users table.
        String loginUsername = registerTokenService.findUsernameForLogin(username);
        return (loginUsername != null);
    }

    /**
     * Reads JSON data from stream
     * 
     * @param request
     * @return
     * @throws IOException
     * @throws JsonMappingException
     * @throws JsonParseException
     */
    @SuppressWarnings("unchecked")
    private final Map<String, String> readJsonFromStream(HttpServletRequest request) throws ActionException {
        try {
            return mapper.readValue(request.getInputStream(), HashMap.class);
        } catch (IOException e) {
            throw new ActionParamsException("Invalid JSON object received");
        }
    }

}
