package com.cognivault.api;
import com.cognivault.readiness.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/health")
public class ReadinessController {private final ReadinessService service;public ReadinessController(ReadinessService service){this.service=service;}
 @GetMapping("/services") public List<ServiceHealth> services(){return service.services();}
 @GetMapping("/readiness") public List<ServiceHealth> readiness(){return service.services();}
}