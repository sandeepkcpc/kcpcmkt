package com.kcpc.mkt;

import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.dto.UserImportBatchResult;
import com.kcpc.mkt.identity.dto.UserImportOutcome;
import com.kcpc.mkt.identity.dto.UserImportRow;
import com.kcpc.mkt.identity.repository.PermissionGrantRepository;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.identity.service.UserCsvImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User CSV Import (Administration -&gt; Users -&gt; Import Users) exercised directly against
 * {@link UserCsvImportService}, the same real Spring context/database every other test in this
 * suite uses - covers validation, duplicate handling, safe partial-success import, no permission
 * auto-grants, and CEO/Marketing Manager Access Class resolution.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserCsvImportTest {

    @Autowired
    UserCsvImportService importService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PermissionGrantRepository permissionGrantRepository;

    private static final String CEO_EMAIL = "ceo@kcpcbandhani.local";

    private User ceo() {
        return userRepository.findByEmailIgnoreCase(CEO_EMAIL).orElseThrow();
    }

    private byte[] csv(String... lines) {
        return String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void validCsvImportsAllRowsAndGeneratesPasswords() {
        long unique = Instant.now().toEpochMilli();
        byte[] bytes = csv(
                "full_name,email,business_role,account_status",
                "Valid One,valid-one-" + unique + "@example.com,Camera Person,ACTIVE",
                "Valid Two,valid-two-" + unique + "@example.com,Video Editor,ACTIVE");

        UserImportBatchResult result = importService.importValidated(ceo(), bytes, "valid.csv");

        assertThat(result.isHasFileLevelErrors()).isFalse();
        assertThat(result.getImportedCount()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(0);
        for (UserImportRow row : result.getRows()) {
            assertThat(row.getOutcome()).isEqualTo(UserImportOutcome.IMPORTED);
            assertThat(row.getGeneratedPassword()).isNotBlank();
            User created = userRepository.findByEmailIgnoreCase(row.getEmail()).orElseThrow();
            assertThat(created.isActive()).isTrue();
        }
    }

    @Test
    void missingRequiredHeaderIsReportedAsFileLevelError() {
        byte[] bytes = csv(
                "full_name,email,account_status", // business_role column missing entirely
                "Someone,someone@example.com,ACTIVE");

        UserImportBatchResult result = importService.validate(bytes, "missing-header.csv");

        assertThat(result.isHasFileLevelErrors()).isTrue();
        assertThat(result.getFileLevelErrors().get(0)).contains("business_role");
        assertThat(result.getRows()).isEmpty();
    }

    @Test
    void invalidEmailFormatIsRejected() {
        long unique = Instant.now().toEpochMilli();
        byte[] bytes = csv(
                "full_name,email,business_role",
                "Bad Email " + unique + ",not-an-email,Camera Person");

        UserImportBatchResult result = importService.validate(bytes, "bad-email.csv");

        assertThat(result.getRows()).hasSize(1);
        UserImportRow row = result.getRows().get(0);
        assertThat(row.isValid()).isFalse();
        assertThat(row.getErrors()).contains("Invalid email format");
    }

    @Test
    void duplicateEmailWithinFileIsRejectedOnSecondOccurrence() {
        long unique = Instant.now().toEpochMilli();
        String email = "dupe-in-file-" + unique + "@example.com";
        byte[] bytes = csv(
                "full_name,email,business_role",
                "First Occurrence," + email + ",Camera Person",
                "Second Occurrence," + email + ",Video Editor");

        UserImportBatchResult result = importService.validate(bytes, "dupe-in-file.csv");

        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows().get(0).isValid()).isTrue();
        UserImportRow second = result.getRows().get(1);
        assertThat(second.isValid()).isFalse();
        assertThat(second.isDuplicateInFile()).isTrue();
        assertThat(second.getErrors()).contains("Duplicate email within this file");
    }

    @Test
    void existingDbUserIsSkippedNotOverwritten() {
        long unique = Instant.now().toEpochMilli();
        byte[] firstPass = csv(
                "full_name,email,business_role",
                "Original Name,existing-" + unique + "@example.com,Camera Person");
        UserImportBatchResult firstResult = importService.importValidated(ceo(), firstPass, "first.csv");
        assertThat(firstResult.getImportedCount()).isEqualTo(1);

        byte[] secondPass = csv(
                "full_name,email,business_role",
                "Attempted Overwrite Name,existing-" + unique + "@example.com,Video Editor");
        UserImportBatchResult secondResult = importService.importValidated(ceo(), secondPass, "second.csv");

        assertThat(secondResult.getImportedCount()).isEqualTo(0);
        assertThat(secondResult.getSkippedExistingCount()).isEqualTo(1);
        User stillOriginal = userRepository.findByEmailIgnoreCase("existing-" + unique + "@example.com").orElseThrow();
        assertThat(stillOriginal.getFullName()).isEqualTo("Original Name");
        assertThat(stillOriginal.getBusinessRole().getRoleName()).isEqualTo("Camera Person");
    }

    @Test
    void unknownBusinessRoleIsRejectedNotFuzzyMatched() {
        long unique = Instant.now().toEpochMilli();
        byte[] bytes = csv(
                "full_name,email,business_role",
                "Typo Role " + unique + ",typo-role-" + unique + "@example.com,Vido Editor");

        UserImportBatchResult result = importService.validate(bytes, "typo-role.csv");

        UserImportRow row = result.getRows().get(0);
        assertThat(row.isValid()).isFalse();
        assertThat(row.getErrors()).anyMatch(e -> e.contains("Unknown Business Role"));
    }

    @Test
    void invalidAccountStatusIsRejectedAndAccessClassIsDerivedNeverAnInput() {
        long unique = Instant.now().toEpochMilli();
        byte[] bytes = csv(
                "full_name,email,business_role,account_status",
                "Bad Status " + unique + ",bad-status-" + unique + "@example.com,Camera Person,SUSPENDED");

        UserImportBatchResult result = importService.validate(bytes, "bad-status.csv");

        UserImportRow row = result.getRows().get(0);
        assertThat(row.isValid()).isFalse();
        assertThat(row.getErrors()).anyMatch(e -> e.contains("Invalid Account Status"));
        // Access Class is still correctly resolved (informational) even though the row is invalid,
        // and it is never itself a CSV column - only ever derived from the Business Role.
        assertThat(row.getResolvedAccessClass()).isEqualTo(AccessClass.EMPLOYEE);
    }

    @Test
    void inactiveAccountStatusCreatesADeactivatedUser() {
        long unique = Instant.now().toEpochMilli();
        byte[] bytes = csv(
                "full_name,email,business_role,account_status",
                "Inactive Import " + unique + ",inactive-import-" + unique + "@example.com,Camera Person,INACTIVE");

        UserImportBatchResult result = importService.importValidated(ceo(), bytes, "inactive.csv");

        assertThat(result.getImportedCount()).isEqualTo(1);
        User created = userRepository.findByEmailIgnoreCase("inactive-import-" + unique + "@example.com").orElseThrow();
        assertThat(created.isActive()).isFalse();
    }

    @Test
    void whitespaceIsTrimmedFromAllFields() {
        long unique = Instant.now().toEpochMilli();
        byte[] bytes = csv(
                "full_name,email,business_role,account_status",
                "  Whitespace Padded " + unique + "  ,  whitespace-" + unique + "@example.com  ,  Camera Person  ,  ACTIVE  ");

        UserImportBatchResult result = importService.importValidated(ceo(), bytes, "whitespace.csv");

        assertThat(result.getImportedCount()).isEqualTo(1);
        User created = userRepository.findByEmailIgnoreCase("whitespace-" + unique + "@example.com").orElseThrow();
        assertThat(created.getFullName()).isEqualTo("Whitespace Padded " + unique);
        assertThat(created.getBusinessRole().getRoleName()).isEqualTo("Camera Person");
    }

    @Test
    void mixedValidAndInvalidRowsImportOnlyTheValidOnes() {
        long unique = Instant.now().toEpochMilli();
        byte[] bytes = csv(
                "full_name,email,business_role",
                "Good Row," + "mixed-good-" + unique + "@example.com,Camera Person",
                "Bad Row,not-an-email,Camera Person",
                "Unknown Role Row,mixed-unknown-" + unique + "@example.com,Nonexistent Role " + unique);

        UserImportBatchResult result = importService.importValidated(ceo(), bytes, "mixed.csv");

        assertThat(result.getTotalRows()).isEqualTo(3);
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getInvalidCount()).isEqualTo(2);
        assertThat(userRepository.findByEmailIgnoreCase("mixed-good-" + unique + "@example.com")).isPresent();
        assertThat(userRepository.findByEmailIgnoreCase("mixed-unknown-" + unique + "@example.com")).isEmpty();
    }

    @Test
    void importedUsersReceiveNoPermissionGrantsAtAll() {
        long unique = Instant.now().toEpochMilli();
        byte[] bytes = csv(
                "full_name,email,business_role",
                "No Grants " + unique + ",no-grants-" + unique + "@example.com,Camera Person");

        UserImportBatchResult result = importService.importValidated(ceo(), bytes, "no-grants.csv");
        assertThat(result.getImportedCount()).isEqualTo(1);

        User created = userRepository.findByEmailIgnoreCase("no-grants-" + unique + "@example.com").orElseThrow();
        assertThat(permissionGrantRepository.findByGrantee(created)).isEmpty();
    }

    @Test
    void ceoAndMarketingManagerBusinessRolesResolveToTheirGovernedAccessClasses() {
        long unique = Instant.now().toEpochMilli();
        byte[] bytes = csv(
                "full_name,email,business_role",
                "MM Import " + unique + ",mm-import-" + unique + "@example.com,Marketing Manager",
                "Employee Import " + unique + ",employee-import-" + unique + "@example.com,Camera Person");

        UserImportBatchResult result = importService.validate(bytes, "access-class.csv");

        UserImportRow mmRow = result.getRows().get(0);
        UserImportRow employeeRow = result.getRows().get(1);
        assertThat(mmRow.getResolvedAccessClass()).isEqualTo(AccessClass.MARKETING_MANAGER);
        assertThat(employeeRow.getResolvedAccessClass()).isEqualTo(AccessClass.EMPLOYEE);
    }

    @Test
    void repeatedImportOfTheSameFileNeverDuplicatesUsers() {
        long unique = Instant.now().toEpochMilli();
        byte[] bytes = csv(
                "full_name,email,business_role",
                "Repeat Import " + unique + ",repeat-import-" + unique + "@example.com,Camera Person");

        UserImportBatchResult first = importService.importValidated(ceo(), bytes, "repeat.csv");
        assertThat(first.getImportedCount()).isEqualTo(1);

        UserImportBatchResult second = importService.importValidated(ceo(), bytes, "repeat.csv");
        assertThat(second.getImportedCount()).isEqualTo(0);
        assertThat(second.getSkippedExistingCount()).isEqualTo(1);

        List<User> matches = userRepository.findAll().stream()
                .filter(u -> u.getEmail().equalsIgnoreCase("repeat-import-" + unique + "@example.com"))
                .toList();
        assertThat(matches).hasSize(1);
    }
}
