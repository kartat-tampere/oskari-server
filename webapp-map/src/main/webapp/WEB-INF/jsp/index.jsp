<%@ page contentType="text/html; charset=UTF-8" isELIgnored="false" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<!DOCTYPE html>
<html>
<head>
    <title>Oskari - ${viewName}</title>

    <script type="text/javascript" src="//code.jquery.com/jquery-1.7.2.min.js">
    </script>
	<script>
  (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
  })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

  ga('create', 'UA-51232341-2', 'auto');
  ga('send', 'pageview');

	</script>
		
    <!-- ############# css ################# -->
    <link
            rel="stylesheet"
            type="text/css"
            href="/Oskari${path}/css/icons.css"/>
    <link
            rel="stylesheet"
            type="text/css"
            href="/Oskari${path}/css/forms.css"/>
    <link
            rel="stylesheet"
            type="text/css"
            href="/Oskari${path}/css/portal.css"/>

    <link
            rel="stylesheet"
            type="text/css"
            href="/Oskari${path}/css/overwritten.css"/>
    <style type="text/css">
        @media screen {
            body {
                margin: 0;
                padding: 0;
            }

            #mapdiv {
                width: 100%;
            }

            #maptools {
                background-color: #333438;
                height: 100%;
                position: absolute;
                top: 0;
                width: 153px;
                z-index: 2;
            }

            #contentMap {
                height: 100%;
                margin-left: 170px;
            }

            #login {
                margin-left: 5px;
            }

            #login input[type="text"], #login input[type="password"] {
                width: 90%;
                margin-bottom: 5px;
                background-image: url("/Oskari${path}/images/forms/input_shadow.png");
                background-repeat: no-repeat;
                padding-left: 5px;
                padding-right: 5px;
                border: 1px solid #B7B7B7;
                border-radius: 4px 4px 4px 4px;
                box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1) inset;
                color: #878787;
                font: 13px/100% Arial,sans-serif;
            }
            #login input[type="submit"] {
                width: 90%;
                margin-bottom: 5px;
                padding-left: 5px;
                padding-right: 5px;
                border: 1px solid #B7B7B7;
                border-radius: 4px 4px 4px 4px;
                box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1) inset;
                color: #878787;
                font: 13px/100% Arial,sans-serif;
            }
            #login p.error {
                font-weight: bold;
                color : red;
                margin-bottom: 10px;
            }

            #login a {
                color: #FFF;
                padding: 5px;
            }
			
		    #tampere_vaakuna{
                position: absolute;
                bottom: 0px;
                width: 100%;
                padding-left: 12px;
                padding-bottom: 4px;
                height: 50px;
                
            }
        }
		@media screen and (max-width: 1400px) {
			#tampere_vaakuna{
				display:none;
		}
    </style>
    <!-- ############# /css ################# -->
</head>
<body>

<nav id="maptools">
    <div id="tampere"><a href="https://kartat.tampere.fi/"><img src="/Oskari${path}/images/tre_kartat_fi.svg"></a></div>
    <div id="loginbar">
    </div>
    <div id="menubar">
    </div>
    <div id="divider">
    </div>
    <div id="toolbar">
    </div>
    <div id="login">
        <c:choose>
            <c:when test="${!empty loginState}">
                <p class="error"><spring:message code="invalid_password_or_username" text="Invalid password or username!" /></p>
            </c:when>
        </c:choose>
         <c:choose>            
            <%-- If logout url is present - so logout link --%>
            <c:when test="${!empty _logout_uri}">
                <a href="${pageContext.request.contextPath}${_logout_uri}">Logout</a>
            </c:when>
            <%-- Otherwise show appropriate logins --%>
            <c:otherwise>
                <c:set var="userIp" value="${header['X-FORWARDED-FOR']}" />           
                <%-- test start --%>
                <%--<c:out value="jorma2"/>--%>
                <c:choose>
                    <c:when test="${fn:startsWith(userIp,'10.')}">
                            <c:if test="${!empty _login_uri_saml}">
                                <a href="${pageContext.request.contextPath}${_login_uri_saml}">Kirjaudu SAMLilla</a><hr />
                            </c:if>
                    </c:when>
                    <c:otherwise></c:otherwise>              
                </c:choose>
                <c:if test="${!empty param.login && !empty _login_uri && !empty _login_field_user}">
                                <form action='${pageContext.request.contextPath}${_login_uri}' method="post" accept-charset="UTF-8">
                                    <input size="16" id="username" name="${_login_field_user}" type="text" placeholder="Username" autofocus
                                           required>
                                    <input size="16" id="password" name="${_login_field_pass}" type="password" placeholder="Password" required>
                                    <input type="submit" id="submit" value="Log in">
                                </form>
                            </c:if>
                <%-- test end --%>                
            </c:otherwise>
        </c:choose>
    </div>
	<div id="tampere_vaakuna"><a href="http://tampere.fi/"><img src="/Oskari${path}/images/tre_vaakuna_vari1.svg"></a></div>
</nav>
<div id="contentMap" class="oskariui container-fluid">
    <div id="menutoolbar" class="container-fluid"></div>
    <div class="row-fluid oskariui-mode-content" style="height: 100%; background-color:white;">
        <div class="oskariui-left"></div>
        <div class="span12 oskariui-center" style="height: 100%; margin: 0;">
            <div id="mapdiv"></div>
        </div>
        <div class="oskari-closed oskariui-right">
            <div id="mapdivB"></div>
        </div>
    </div>
</div>


<!-- ############# Javascript ################# -->

<!--  OSKARI -->

<script type="text/javascript">
    var ajaxUrl = '${ajaxUrl}';
    var viewId = '${viewId}';
    var language = '${language}';
    var preloaded = ${preloaded};
    var controlParams = ${controlParams};
</script>

<script type="text/javascript"
        src="/Oskari/bundles/bundle.js">
</script>

<!--  OPENLAYERS -->
<script type="text/javascript"
        src="/Oskari/packages/openlayers/startup.js">
</script>

<c:if test="${preloaded}">
    <!-- Pre-compiled application JS, empty unless created by build job -->
    <script type="text/javascript"
            src="/Oskari${path}/oskari.min.js">
    </script>
    <!-- Minified CSS for preload -->
    <link
            rel="stylesheet"
            type="text/css"
            href="/Oskari${path}/oskari.min.css"
            />
    <%--language files --%>
    <script type="text/javascript"
            src="/Oskari${path}/oskari_lang_all.js">
    </script>
    <script type="text/javascript"
            src="/Oskari${path}/oskari_lang_${language}.js">
    </script>
</c:if>

<script type="text/javascript"
        src="/Oskari${path}/index.js">
</script>


<!-- ############# /Javascript ################# -->
</body>
</html>
