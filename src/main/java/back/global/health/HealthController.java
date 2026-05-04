package back.global.health;

import back.global.response.RsData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/v1/health")
    public RsData<String> health() {
        return new RsData<>("OK", "서버 정상 동작 중");
    }
}