-include= ~${workspace}/cnf/resources/bnd/feature.props
symbolicName=com.ibm.websphere.appserver.servlet-4.0
WLP-DisableAllFeatures-OnConflict: false
visibility=public
singleton=true
IBM-App-ForceRestart: install, uninstall
IBM-ShortName: servlet-4.0
IBM-API-Package: javax.servlet.annotation;  type="spec", \
 javax.servlet.descriptor;  type="spec", \
 javax.servlet.resources;  type="spec", \
 javax.servlet.http;  type="spec", \
 javax.servlet;  type="spec", \
 com.ibm.websphere.servlet.session;  type="ibm-api", \
 com.ibm.wsspi.servlet.session;  type="ibm-api", \
 com.ibm.websphere.servlet.error;  type="ibm-api", \
 com.ibm.websphere.servlet.container;  type="ibm-api", \
 com.ibm.websphere.servlet.context;  type="ibm-api", \
 com.ibm.websphere.servlet.event;  type="ibm-api", \
 com.ibm.websphere.webcontainer;  type="ibm-api", \
 com.ibm.wsspi.webcontainer;  type="internal", \
 com.ibm.wsspi.webcontainer.annotation;  type="internal"
IBM-SPI-Package: com.ibm.wsspi.webcontainer, \
 com.ibm.wsspi.webcontainer.collaborator, \
 com.ibm.wsspi.webcontainer.extension, \
 com.ibm.wsspi.webcontainer.filter, \
 com.ibm.wsspi.webcontainer.metadata, \
 com.ibm.wsspi.webcontainer.osgi.extension, \
 com.ibm.wsspi.webcontainer.servlet, \
 com.ibm.wsspi.webcontainer.webapp, \
 com.ibm.websphere.servlet.filter, \
 com.ibm.websphere.servlet.response, \
 com.ibm.ws.webcontainer.extension, \
 com.ibm.websphere.servlet.request, \
 com.ibm.ws.webcontainer.spiadapter.collaborator, \
 com.ibm.websphere.webcontainer.async
Subsystem-Category: JavaEE8Application
-features=io.openliberty.servlet.api-4.0, \
  com.ibm.websphere.appserver.javaeeddSchema-1.0, \
  com.ibm.websphere.appserver.javaeePlatform-8.0, \
  com.ibm.websphere.appserver.eeCompatible-8.0, \
  com.ibm.websphere.appserver.servlet-servletSpi1.0, \
  com.ibm.websphere.appserver.httptransport-1.0, \
  com.ibm.websphere.appserver.requestProbes-1.0, \
  com.ibm.websphere.appserver.javax.annotation-1.3, \
  com.ibm.websphere.appserver.injection-1.0
-bundles=com.ibm.ws.app.manager.war, \
 com.ibm.ws.managedobject, \
 com.ibm.ws.org.apache.commons.io, \
 com.ibm.websphere.security, \
 com.ibm.ws.org.apache.commons.fileupload, \
 com.ibm.ws.webcontainer.servlet.4.0, \
 com.ibm.ws.webcontainer.servlet.4.0.factories, \
 com.ibm.ws.webcontainer.servlet.3.1, \
 com.ibm.ws.session, \
 com.ibm.ws.webcontainer, \
 com.ibm.ws.webcontainer.cors, \
 com.ibm.ws.http.plugin.merge, \
 com.ibm.ws.webserver.plugin.runtime, \
 com.ibm.ws.webserver.plugin.runtime.interfaces
-jars=com.ibm.ws.webserver.plugin.utility, \
 com.ibm.websphere.appserver.api.servlet; location:=dev/api/ibm/
-files=bin/tools/ws-webserverPluginutil.jar, \
 bin/pluginUtility; ibm.executable:=true; ibm.file.encoding:=ebcdic, \
 bin/pluginUtility.bat, \
 dev/api/ibm/javadoc/com.ibm.websphere.appserver.api.servlet_1.1-javadoc.zip
Subsystem-Name: Java Servlets 4.0
kind=ga
edition=core
WLP-Activation-Type: parallel
WLP-InstantOn-Enabled: true
