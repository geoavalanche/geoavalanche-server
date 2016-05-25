FROM tomcat:8-jre8

# Set the GEOSERVER_DATA_DIR
RUN mkdir -p /var/lib/geoavalanche/data
VOLUME /var/lib/geoavalanche/data
ENV GEOSERVER_DATA_DIR /var/lib/geoavalanche/data

# Add the WAR artifact
ADD ./src/main/target/geoavalanche.war ${CATALINA_HOME}/webapps
CMD ["catalina.sh", "run"]