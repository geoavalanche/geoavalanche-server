FROM tomcat:8-jre8

###############################################
# Modify Tomcat configuration for enabling CORS
RUN mkdir -p /tmp/tomcat/conf
ENV TOMCAT_TMP /tmp/tomcat/conf

# COPY assets/cors-web.xml ${TOMCAT_TMP}
# COPY assets/splicewebxml.sh ${TOMCAT_TMP}
COPY assets/web.xml.cors ${TOMCAT_TMP}

WORKDIR ${TOMCAT_TMP}
# RUN chmod +x ./splicewebxml.sh
# RUN ./splicewebxml.sh cors-web.xml ${CATALINA_HOME}/conf/web.xml
RUN cp web.xml.cors ${CATALINA_HOME}/conf/web.xml
#################################################

# Set the GEOSERVER_DATA_DIR
RUN mkdir -p /var/lib/geoavalanche/data
VOLUME /var/lib/geoavalanche/data
ENV GEOSERVER_DATA_DIR /var/lib/geoavalanche/data

# Set CATALINA_OPTS for debugging
ENV CATALINA_OPTS "-Xdebug -Xrunjdwp:transport=dt_socket,address=8888,server=y,suspend=n"

# Add the WAR artifact
WORKDIR ${CATALINA_HOME}
ADD ./src/main/target/geoavalanche.war ${CATALINA_HOME}/webapps
CMD ["catalina.sh", "run"]

ENV MONGOIP="localhost"
ENV MONGOPORT="27017"

# Expose the port for debugging
EXPOSE 8888