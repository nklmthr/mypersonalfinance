package com.nklmthr.finance.personal.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontEndController {

	@GetMapping({"/{path:^(?!api$)[^\\.]+}", "/{path:^(?!api$)[^\\.]+}/{subpath:[^\\.]+}"})
	public String forward() {
	    return "forward:/index.html";
	}
}
