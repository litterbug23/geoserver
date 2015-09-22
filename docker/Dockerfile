FROM ubuntu:14.04.3
MAINTAINER Juha Hyvärinen <juha.hyvarinen@cyberlightning.com>

# LABEL Description="Fiware GIS Data Provider"

RUN apt-get update && apt-get -y install \
    tomcat7 \
    openjdk-7-jdk \
    wget \
    git \
    maven2 \
    unzip

ENV JAVA_HOME /usr/lib/jvm/java-7-openjdk-amd64
ENV CATALINA_HOME /usr/share/tomcat7
ENV PATH $PATH:$CATALINA_HOME/bin

# Configure Tomcat 7 directories 
RUN ln -s /var/lib/tomcat7/common $CATALINA_HOME/common && \
    ln -s /var/lib/tomcat7/server $CATALINA_HOME/server && \
    ln -s /var/lib/tomcat7/shared $CATALINA_HOME/shared && \
    ln -s /etc/tomcat7 $CATALINA_HOME/conf && \
    mkdir $CATALINA_HOME/temp && \
    mkdir $CATALINA_HOME/webapps

# Download geoserver sources and install it
RUN \
    git clone -b fiware_rel_4.x https://github.com/Cyberlightning/geoserver.git && \
    cd /geoserver/src && \
    mvn clean install -DdownloadSources=false -Pw3ds -Pdds && \
    cd /geoserver && \
    mkdir -p /usr/share/tomcat7/webapps/geoserver/ && \
    cp -R src/web/app/target/geoserver /usr/share/tomcat7/webapps/

# Download and unzip test client
RUN \
    cd /usr/share/tomcat7/webapps/ && \
    wget --no-check-certificate https://forge.fiware.org/frs/download.php/1672/GISDataProvider-test_client_4.4.2.zip && \
    unzip GISDataProvider-test_client_4.4.2.zip && \
    rm GISDataProvider-test_client_4.4.2.zip

RUN \
    cd /usr/share/tomcat7/webapps/geoserver/data && \
    wget --no-check-certificate https://forge.fiware.org/frs/download.php/1687/FIWARE-Test_asset_4.4.3.zip && \
    unzip FIWARE-Test_asset_4.4.3.zip && \
    rm FIWARE-Test_asset_4.4.3.zip    

EXPOSE 8080
CMD ["catalina.sh", "run"]
