FROM tomcat:8-jre8
# ENV GEOSERVER_DATA_DIR ${CATALINA_HOME}/webapps/geoavalanche/data
ADD ./src/main/target/geoavalanche.war ${CATALINA_HOME}/webapps
# ADD ./src/main/target/geoavalanche.war /tmp
# RUN mv /tmp/geoavalanche.war ${CATALINA_HOME}/webapps/
CMD ["catalina.sh", "run"]
# CMD ${CATALINA_HOME}/bin/catalina.sh run
# WORKDIR $CATALINA_HOME
# ENV PATH $CATALINA_HOME/bin:$PATH
# CMD ["tail","-100f","logs/catalina.out"]
# VOLUME $GEOSERVER_DATA_DIR
# RUN mkdir -p /var/lib/geoserver/data
# VOLUME /var/lib/geoserver/data

# ENV GEOSERVER_DATA_DIR /var/lib/geoserver/data