package com.spshpau.chatservice.controller.dto;

import com.spshpau.chatservice.controller.dto.enums.ExperienceLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ProducerProfileSummaryDto {
    private boolean availability;
    private ExperienceLevel experienceLevel;
}
