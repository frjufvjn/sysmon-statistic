![Java CI](https://github.com/frjufvjn/sysmon-statistic/workflows/Java%20CI/badge.svg?branch=dev)
# sysmon-statistic

Hansol Inticube IS-MON statistic        
  - IS-MON statistic independent server module
  - By separating the heavy statistics DB transaction process, the load is distributed to ensure stable processing of the main application job.
  - Increases DB transaction performance and efficiency by collecting and storing unit storage for a certain period of time at a time for unit DB transaction in which cases are processed.
  - Remove unnecessary real-time data DB transaction and manage as memory and provide as api

### Building

To launch your tests:
```
./mvnw clean test
```

To package your application:
```
./mvnw clean package
```

To run your application:
```
./mvnw clean compile exec:java
or 
java -jar target/statistic-1.0.0-SNAPSHOT-fat
```
