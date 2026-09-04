package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.common.error.ApiErrorResponse;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.notification.domain.Notification;
import com.kcpc.mkt.notification.service.NotificationService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/** "View all notifications" page + read/unread actions - see NotificationService's own javadoc
 * for the duplicate-prevention/authorization contract this reuses unchanged. */
@Controller
@RequestMapping("/app/notifications")
public class NotificationMvcController {

    private final NotificationService notificationService;

    public NotificationMvcController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        var notifications = notificationService.listAll(principal.user());
        model.addAttribute("notifications", notifications);
        model.addAttribute("hasUnread", notifications.stream().anyMatch(Notification::isUnread));
        return "notifications";
    }

    /** Same X-Requested-With: fetch convention DeliverableMvcController already uses - the header
     * dropdown marks a single notification read via fetch (no page reload), while the "View all"
     * page's own per-row form falls back to a plain redirect. */
    @PostMapping("/{id}/read")
    public Object markRead(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal,
                            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                            RedirectAttributes ra, HttpServletRequest request) {
        try {
            notificationService.markRead(principal.user(), id);
            if (isAjax(requestedWith)) {
                return ResponseEntity.ok().build();
            }
        } catch (DomainException e) {
            if (isAjax(requestedWith)) {
                return ResponseEntity.status(e.getHttpStatus())
                        .body(ApiErrorResponse.of(e.getErrorCode(), e.getMessage(), request.getRequestURI()));
            }
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/notifications";
    }

    @PostMapping("/mark-all-read")
    public Object markAllRead(@AuthenticationPrincipal KcpcUserPrincipal principal,
                               @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) {
        notificationService.markAllRead(principal.user());
        if (isAjax(requestedWith)) {
            return ResponseEntity.ok().build();
        }
        return "redirect:/app/notifications";
    }

    private static boolean isAjax(String requestedWith) {
        return "fetch".equals(requestedWith);
    }
}
