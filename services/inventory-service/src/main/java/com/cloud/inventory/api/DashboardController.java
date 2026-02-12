package com.cloud.inventory.api;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Hidden
public class DashboardController {

    @GetMapping({"/dashboard", "/dashboard/release-audit"})
    public String dashboard() {
        return "redirect:/release-dashboard.html";
    }
}
