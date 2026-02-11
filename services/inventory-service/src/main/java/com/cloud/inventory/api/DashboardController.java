package com.cloud.inventory.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping({"/dashboard", "/dashboard/release-audit"})
    public String dashboard() {
        return "redirect:/release-dashboard.html";
    }
}
