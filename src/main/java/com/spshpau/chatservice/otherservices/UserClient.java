package com.spshpau.chatservice.otherservices;

import com.spshpau.chatservice.controller.dto.UserSummaryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "userservice", url = "${application.cofig.userclienturl}")
public interface UserClient {
    @GetMapping("/me/connections/all")
    List<UserSummaryDto> findConnectionsByJwt(@RequestHeader("Authorization") String bearerToken);
}
