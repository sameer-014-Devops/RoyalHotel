FROM openjdk:17
COPY ./target/*jar royalhotel.jar
ENTRYPOINT ["java","-jar","/royalhotel.jar"]