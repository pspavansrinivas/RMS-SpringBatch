package au.nsw.rms.boot.encryptfilebatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EncryptfilebatchApplication {

    private static Logger logger = LoggerFactory.getLogger(EncryptfilebatchApplication.class);


    public static void main(String[] args) {
        SpringApplication.run(EncryptfilebatchApplication.class, args);
    }
}
