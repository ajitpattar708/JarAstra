package com.example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

@Controller("/hello")
public class VulnerableController {

    private static final Logger logger = LogManager.getLogger(VulnerableController.class);

    @Get("/")
    public Map<String, String> hello() throws Exception {
        logger.info("Micronaut handling request with vulnerable Log4j2!");
        
        ObjectMapper mapper = new ObjectMapper();
        String json = "{\"framework\": \"micronaut\", \"security\": \"none\"}";
        
        return mapper.readValue(json, Map.class);
    }
}
