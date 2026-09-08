package com.sporty.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProbeController {

  @PostMapping("/probe")
  void probe(@Valid @RequestBody ProbeRequest request) {}

  record ProbeRequest(@NotBlank String value) {}
}
