package fi.nls.oskari.control.users;

import fi.nls.oskari.control.ActionParameters;

import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.util.Calendar;

/**
 * Created by SMAKINEN on 1.9.2016.
 */
public class RegistrationUtil {

    public static final String getServerAddress(ActionParameters params) {
        // TODO: why not use oskari.domain from oskari-ext.properties?
        final HttpServletRequest request = params.getRequest();
        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
    }

    /**
     * Create timestamp for 2 days as expirytime.
     * @return
     */
    public static Timestamp createExpiryTime(){
        Calendar calender = Calendar.getInstance();
        Timestamp currentTime = new java.sql.Timestamp(calender.getTime().getTime());
        calender.setTime(currentTime);
        calender.add(Calendar.DAY_OF_MONTH, 2);
        Timestamp expiryTime = new java.sql.Timestamp(calender.getTime().getTime());
        return expiryTime;
    }
}
