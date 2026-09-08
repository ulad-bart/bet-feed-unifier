package com.sporty.error;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProbeController.class)
@Import(FeedErrorHandler.class)
class FeedErrorHandlerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void methodArgumentNotValid_returnsBadRequestProblemDetail() throws Exception {
    mockMvc.perform(post("/probe")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
        .andExpect(jsonPath("$.detail").value(not("")));
  }

  @Test
  void malformedJsonBody_returnsBadRequestProblemDetail() throws Exception {
    mockMvc.perform(post("/probe")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{not-json"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
        .andExpect(jsonPath("$.detail").value(not("")));
  }
}
