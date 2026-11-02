package com.bankmanagement.organization.dto;

import com.bankmanagement.organization.domain.Announcement;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public final class AnnouncementDto {

    private AnnouncementDto() {
    }

    /** {@code branchId} null means organization-wide; Branch Managers may only post to their branch. */
    public record Request(
            @NotBlank @Size(min = 3, max = 120) @Pattern(regexp = Patterns.SAFE_TEXT) String title,
            @NotBlank @Size(min = 3, max = 2000) @Pattern(regexp = Patterns.SAFE_TEXT) String body,
            boolean pinned,
            UUID branchId,
            @FutureOrPresent LocalDate expiresOn) {
    }

    public record Response(UUID announcementId, String title, String body, boolean pinned, UUID branchId, String authorName,
                           LocalDate publishedOn, LocalDate expiresOn) {

        public static Response from(Announcement a) {
            return new Response(a.getId(), a.getTitle(), a.getBody(), a.isPinned(), a.getBranchId(),
                    a.getAuthorName(), a.getPublishedOn(), a.getExpiresOn());
        }
    }
}
