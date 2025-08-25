package com.nklmthr.finance.personal.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontEndController {

	@GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }

    @GetMapping("/{path:[^\\.]+}")
    public String anySingle() {
        return "forward:/index.html";
    }

    @GetMapping("/{path:[^\\.]+}/{subpath:[^\\.]+}")
    public String anyDouble() {
        return "forward:/index.html";
    }
}
