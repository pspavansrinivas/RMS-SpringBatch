# Project Title

A Spring Boot Batch application that take two input parameters, the first one is path to a text file, the second one is number of threads. The batch  should take the file input and split the file content by lines to each thread and run Caesar cipher for each line, the final output should be an encrypted text file.

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. See deployment for notes on how to deploy the project on a live system.

I have added a sample file called process.txt

### Prerequisites

- Maven 3 installed
- Java 8 installed

### Installing

Run Maven compile or install

```
./mvnw install
```

## Running the application

To run the application execute the following command,

```
./mvnw spring-boot:run -Dspring-boot.run.arguments=--fullFilePath=./process.txt,--threadCount=10,--cipherKeyShiftValue=5,--outputFileName=process.txt
```

####Parameters

- fullFilePath - (Mandatory)
Input file to process. It should be full file path. Although the batch tries to normalize, it is better to give full file path.
- threadCount - (default=5)
Thread concurrency limit
- cipherKeyShiftValue - (default=3)
Cipher value to shift can be customized by passing parameter. Integer value to do enable ciphering.
- outputFileName - (default={same_filename})
Output will be placed in the directory called encrypted with the same filename if its not passed

####Different examples to run the applications

```
./mvnw spring-boot:run -Dspring-boot.run.arguments=--fullFilePath=./process.txt,--threadCount=10
```

To Run the application with different cipher value
```
./mvnw spring-boot:run -Dspring-boot.run.arguments=--fullFilePath=./process.txt,--threadCount=10,--cipherKeyShiftValue=5
```