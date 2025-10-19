package com.nklmthr.finance.personal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Institution;
import com.nklmthr.finance.personal.repository.InstitutionRepository;

@ExtendWith(MockitoExtension.class)
class InstitutionServiceTest {

    @Mock
    private AppUserService appUserService;

    @Mock
    private InstitutionRepository institutionRepository;

    @InjectMocks
    private InstitutionService institutionService;

    private AppUser currentUser;

    @BeforeEach
    void setUp() {
        currentUser = AppUser.builder()
            .id("user-1")
            .username("jane.doe")
            .password("pass")
            .role("USER")
            .email("jane@example.com")
            .build();
        when(appUserService.getCurrentUser()).thenReturn(currentUser);

        // Ensure @Autowired fields are set on the service
        ReflectionTestUtils.setField(institutionService, "appUserService", appUserService);
        ReflectionTestUtils.setField(institutionService, "institutionRepository", institutionRepository);
    }

    @Test
    void getAllInstitutions_returnsUserScopedList() {
        Institution i1 = Institution.builder().id("i1").name("Bank A").appUser(currentUser).build();
        Institution i2 = Institution.builder().id("i2").name("Bank B").appUser(currentUser).build();
        when(institutionRepository.findAllByAppUser(currentUser)).thenReturn(List.of(i1, i2));

        List<Institution> result = institutionService.getAllInstitutions();

        assertThat(result).containsExactly(i1, i2);
    }

    @Test
    void getInstitutionById_returnsWhenFound() {
        Institution inst = Institution.builder().id("id-1").name("Bank").appUser(currentUser).build();
        when(institutionRepository.findByAppUserAndId(currentUser, "id-1")).thenReturn(Optional.of(inst));

        Optional<Institution> result = institutionService.getInstitutionById("id-1");

        assertThat(result).contains(inst);
    }

    @Test
    void getInstitutionById_emptyWhenMissing() {
        when(institutionRepository.findByAppUserAndId(currentUser, "missing")).thenReturn(Optional.empty());

        Optional<Institution> result = institutionService.getInstitutionById("missing");

        assertThat(result).isEmpty();
    }

    @Test
    void createInstitution_succeedsWhenNameUnique() {
        Institution toCreate = Institution.builder().name("New Bank").description("d").build();
        Institution saved = Institution.builder().id("i1").name("New Bank").description("d").appUser(currentUser).build();
        when(institutionRepository.existsByAppUserAndName(currentUser, "New Bank")).thenReturn(false);
        when(institutionRepository.save(any(Institution.class))).thenReturn(saved);

        Institution result = institutionService.createInstitution(toCreate);

        assertThat(result).isEqualTo(saved);
        assertThat(result.getAppUser()).isEqualTo(currentUser);
        verify(institutionRepository, times(1)).save(any(Institution.class));
    }

    @Test
    void createInstitution_throwsWhenDuplicateName() {
        Institution toCreate = Institution.builder().name("Bank").build();
        when(institutionRepository.existsByAppUserAndName(currentUser, "Bank")).thenReturn(true);

        assertThatThrownBy(() -> institutionService.createInstitution(toCreate))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void updateInstitution_updatesFieldsWhenFound() {
        String id = "id-1";
        Institution existing = Institution.builder().id(id).name("Old").description("old").appUser(currentUser).build();
        Institution update = Institution.builder().id(id).name("New").description("new").build();
        Institution saved = Institution.builder().id(id).name("New").description("new").appUser(currentUser).build();

        when(institutionRepository.findByAppUserAndId(currentUser, id)).thenReturn(Optional.of(existing));
        when(institutionRepository.save(existing)).thenReturn(saved);

        Institution result = institutionService.updateInstitution(id, update);

        assertThat(result).isEqualTo(saved);
        assertThat(existing.getName()).isEqualTo("New");
        assertThat(existing.getDescription()).isEqualTo("new");
        assertThat(existing.getAppUser()).isEqualTo(currentUser);
        verify(institutionRepository).save(existing);
    }

    @Test
    void updateInstitution_throwsWhenMissing() {
        String id = "missing";
        Institution update = Institution.builder().name("New").build();
        when(institutionRepository.findByAppUserAndId(currentUser, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> institutionService.updateInstitution(id, update))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void deleteInstitution_deletesWhenExists() {
        String id = "id-1";
        Institution existing = Institution.builder().id(id).name("Bank").appUser(currentUser).build();
        when(institutionRepository.findByAppUserAndId(currentUser, id)).thenReturn(Optional.of(existing));

        institutionService.deleteInstitution(id);

        verify(institutionRepository, times(1)).deleteByAppUserAndId(currentUser, id);
    }

    @Test
    void deleteInstitution_throwsWhenMissing() {
        String id = "missing";
        when(institutionRepository.findByAppUserAndId(currentUser, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> institutionService.deleteInstitution(id))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }
}


