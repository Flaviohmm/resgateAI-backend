package br.com.resgateai.controller;

import br.com.resgateai.dto.response.DashboardResponse;
import br.com.resgateai.service.SustainabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sustentabilidade")
@RequiredArgsConstructor
public class SustentabilidadeController {

    private final SustainabilityService sustainabilityService;

    @GetMapping
    public DashboardResponse dashboard() {
        return sustainabilityService.dashboard();
    }
}
