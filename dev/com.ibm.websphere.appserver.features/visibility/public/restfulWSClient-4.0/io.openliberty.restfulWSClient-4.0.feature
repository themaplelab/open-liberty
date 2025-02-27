-include= ~${workspace}/cnf/resources/bnd/feature.props
symbolicName=io.openliberty.restfulWSClient-4.0
visibility=public
singleton=true
IBM-API-Package: jakarta.ws.rs; type="spec", \
 jakarta.ws.rs.container; type="spec", \
 jakarta.ws.rs.core; type="spec", \
 jakarta.ws.rs.client; type="spec", \
 jakarta.ws.rs.ext; type="spec", \
 jakarta.ws.rs.sse; type="spec", \
 com.ibm.websphere.jaxrs20.multipart; type="ibm-api", \
 org.jboss.resteasy.annotations; type="internal", \
 org.jboss.resteasy.client.jaxrs; type="internal", \
 org.jboss.resteasy.client.jaxrs.internal; type="internal", \
 org.jboss.resteasy.plugins.providers.multipart; type="internal", \
 org.jboss.resteasy.plugins.providers.sse.client; type="internal", \
 org.jboss.resteasy.plugins.providers.sse; type="internal", \
 org.jboss.resteasy.plugins.providers; type="internal", \
 org.jboss.resteasy.spi;type="internal", \
 org.reactivestreams;type="internal"
IBM-App-ForceRestart: uninstall, \
 install
IBM-ShortName: restfulWSClient-4.0
WLP-AlsoKnownAs: jaxrsClient-4.0
Subsystem-Name: Jakarta RESTful Web Services 4.0 Client
-features=io.openliberty.cdi-4.1, \
  io.openliberty.jakarta.mail-2.1, \
  io.openliberty.jakarta.validation-3.1, \
  com.ibm.websphere.appserver.eeCompatible-11.0, \
  io.openliberty.servlet.internal-6.1, \
  io.openliberty.jakarta.restfulWS-4.0, \
  com.ibm.websphere.appserver.org.reactivestreams.reactive-streams-1.0, \
  com.ibm.websphere.appserver.jndi-1.0, \
  io.openliberty.activation.internal-2.1, \
  io.openliberty.jsonp-2.1
-bundles=\
  io.openliberty.jaxrs30; location:="dev/api/ibm/,lib/", \
  com.ibm.ws.jaxrs.2.x.config, \
  io.openliberty.org.apache.commons.codec, \
  io.openliberty.org.apache.commons.logging, \
  com.ibm.ws.org.apache.httpcomponents, \
  io.openliberty.org.jboss.logging35, \
  io.openliberty.org.jboss.resteasy.common.ee10
-files=\
  dev/api/ibm/javadoc/io.openliberty.jaxrs30_1.0-javadoc.zip
kind=noship
edition=full
WLP-Activation-Type: parallel
WLP-InstantOn-Enabled: true
