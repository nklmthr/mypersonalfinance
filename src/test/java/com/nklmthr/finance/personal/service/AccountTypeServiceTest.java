package com.nklmthr.finance.personal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.nklmthr.finance.personal.dto.AccountTypeDTO;
import com.nklmthr.finance.personal.mapper.AccountTypeMapper;
import com.nklmthr.finance.personal.model.AccountType;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AccountTypeRepository;

@ExtendWith(MockitoExtension.class)
class AccountTypeServiceTest {

    @Mock
    private AppUserService appUserService;

    @Mock
    private AccountTypeRepository accountTypeRepository;

    @Mock
    private AccountTypeMapper accountTypeMapper;

    @InjectMocks
    private AccountTypeService accountTypeService;

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

        // Ensure fields annotated with @Autowired are set for unit testing
        ReflectionTestUtils.setField(accountTypeService, "appUserService", appUserService);
        ReflectionTestUtils.setField(accountTypeService, "accountTypeRepository", accountTypeRepository);
    }

    @Test
    void create_succeeds_whenNameUniqueForUser() {
        AccountTypeDTO inputDto = new AccountTypeDTO(null, "Savings", "desc", "ASSET", BigDecimal.ZERO);
        AccountType entityToSave = AccountType.builder()
            .id(null)
            .name("Savings")
            .description("desc")
            .classification("ASSET")
            .build();
        AccountType savedEntity = AccountType.builder()
            .id("id-1")
            .name("Savings")
            .description("desc")
            .classification("ASSET")
            .appUser(currentUser)
            .build();
        AccountTypeDTO expectedDto = new AccountTypeDTO("id-1", "Savings", "desc", "ASSET", null);

        when(accountTypeRepository.existsByAppUserAndName(currentUser, "Savings")).thenReturn(false);
        when(accountTypeMapper.toEntity(inputDto)).thenReturn(entityToSave);
        when(accountTypeRepository.save(any(AccountType.class))).thenReturn(savedEntity);
        when(accountTypeMapper.toDTO(savedEntity)).thenReturn(expectedDto);

        AccountTypeDTO result = accountTypeService.create(inputDto);

        assertThat(result).isEqualTo(expectedDto);
        verify(accountTypeRepository, times(1)).save(any(AccountType.class));
    }

    @Test
    void create_throws_whenNameExistsForUser() {
        AccountTypeDTO inputDto = new AccountTypeDTO(null, "Savings", null, null, null);
        when(accountTypeRepository.existsByAppUserAndName(currentUser, "Savings")).thenReturn(true);

        assertThatThrownBy(() -> accountTypeService.create(inputDto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void getAll_returnsMappedListForCurrentUser() {
        AccountType at1 = AccountType.builder().id("a1").name("Savings").appUser(currentUser).build();
        AccountType at2 = AccountType.builder().id("a2").name("Checking").appUser(currentUser).build();
        when(accountTypeRepository.findByAppUser(currentUser)).thenReturn(List.of(at1, at2));

        AccountTypeDTO dto1 = new AccountTypeDTO("a1", "Savings", null, null, null);
        AccountTypeDTO dto2 = new AccountTypeDTO("a2", "Checking", null, null, null);
        when(accountTypeMapper.toDTOList(List.of(at1, at2))).thenReturn(List.of(dto1, dto2));

        List<AccountTypeDTO> result = accountTypeService.getAll();

        assertThat(result).containsExactly(dto1, dto2);
    }

    @Test
    void getById_returnsDtoWhenFoundForUser() {
        String id = "id-123";
        AccountType entity = AccountType.builder().id(id).name("Savings").appUser(currentUser).build();
        AccountTypeDTO dto = new AccountTypeDTO(id, "Savings", null, null, null);
        when(accountTypeRepository.findByAppUserAndId(currentUser, id)).thenReturn(Optional.of(entity));
        when(accountTypeMapper.toDTO(entity)).thenReturn(dto);

        Optional<AccountTypeDTO> result = accountTypeService.getById(id);

        assertThat(result).contains(dto);
    }

    @Test
    void getById_returnsEmptyWhenNotFoundForUser() {
        String id = "missing";
        when(accountTypeRepository.findByAppUserAndId(currentUser, id)).thenReturn(Optional.empty());

        Optional<AccountTypeDTO> result = accountTypeService.getById(id);

        assertThat(result).isEmpty();
    }

    @Test
    void getByName_returnsDtoWhenFoundForUser() {
        String name = "Savings";
        AccountType entity = AccountType.builder().id("id-1").name(name).appUser(currentUser).build();
        AccountTypeDTO dto = new AccountTypeDTO("id-1", name, null, null, null);
        when(accountTypeRepository.findByAppUserAndName(currentUser, name)).thenReturn(Optional.of(entity));
        when(accountTypeMapper.toDTO(entity)).thenReturn(dto);

        Optional<AccountTypeDTO> result = accountTypeService.getByName(name);

        assertThat(result).contains(dto);
    }

    @Test
    void getByName_returnsEmptyWhenNotFoundForUser() {
        String name = "Unknown";
        when(accountTypeRepository.findByAppUserAndName(currentUser, name)).thenReturn(Optional.empty());

        Optional<AccountTypeDTO> result = accountTypeService.getByName(name);

        assertThat(result).isEmpty();
    }

    @Test
    void update_updatesExistingEntityAndReturnsMappedDto() {
        String id = "id-1";
        AccountType existing = AccountType.builder()
            .id(id)
            .name("Old")
            .description("old")
            .classification("ASSET")
            .appUser(currentUser)
            .build();
        AccountTypeDTO updateDto = new AccountTypeDTO(id, "New", "new", "LIABILITY", BigDecimal.TEN);
        AccountType saved = AccountType.builder()
            .id(id)
            .name("New")
            .description("new")
            .classification("LIABILITY")
            .appUser(currentUser)
            .build();
        AccountTypeDTO expected = new AccountTypeDTO(id, "New", "new", "LIABILITY", BigDecimal.TEN);

        when(accountTypeRepository.findByAppUserAndId(currentUser, id)).thenReturn(Optional.of(existing));
        when(accountTypeRepository.save(existing)).thenReturn(saved);
        when(accountTypeMapper.toDTO(saved)).thenReturn(expected);

        AccountTypeDTO result = accountTypeService.update(id, updateDto);

        assertThat(result).isEqualTo(expected);
        assertThat(existing.getName()).isEqualTo("New");
        assertThat(existing.getDescription()).isEqualTo("new");
        assertThat(existing.getClassification()).isEqualTo("LIABILITY");
        assertThat(existing.getAppUser()).isEqualTo(currentUser);
        verify(accountTypeRepository).save(existing);
    }

    @Test
    void update_throwsWhenEntityNotFound() {
        String id = "missing";
        when(accountTypeRepository.findByAppUserAndId(currentUser, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountTypeService.update(id, new AccountTypeDTO(id, "n", null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void delete_deletesWhenExistsForUser() {
        String id = "id-1";
        when(accountTypeRepository.existsByAppUserAndId(currentUser, id)).thenReturn(true);

        accountTypeService.delete(id);

        verify(accountTypeRepository, times(1)).deleteByAppUserAndId(currentUser, id);
    }

    @Test
    void delete_throwsWhenMissingForUser() {
        String id = "missing";
        when(accountTypeRepository.existsByAppUserAndId(currentUser, id)).thenReturn(false);

        assertThatThrownBy(() -> accountTypeService.delete(id))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }
}


