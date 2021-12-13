FROM rainee/docker-alpine-oraclejdk8
COPY ./java/registry/target/registry.jar /home/opensaber/registry.jar
RUN mkdir /home/opensaber/config
CMD ["java", "-Xms1024m", "-Xmx2048m", "-XX:+UseG1GC", "-Dlog4j2.formatMsgNoLookups=true","-jar", "/home/opensaber/registry.jar"]
