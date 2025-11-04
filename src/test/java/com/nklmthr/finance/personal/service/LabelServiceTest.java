package com.nklmthr.finance.personal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.nklmthr.finance.personal.dto.LabelDTO;
import com.nklmthr.finance.personal.mapper.LabelMapper;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Label;
import com.nklmthr.finance.personal.repository.LabelRepository;

@ExtendWith(MockitoExtension.class)
class LabelServiceTest {

    @Mock
    private AppUserService appUserService;
    @Mock
    private LabelRepository labelRepository;
    @Mock
    private LabelMapper labelMapper;
    @InjectMocks
    private LabelService service;

    private AppUser user;

    @BeforeEach
    void setUp() {
        user = AppUser.builder().id("u1").username("jane").password("p").role("USER").email("j@e.com").build();
        lenient().when(appUserService.getCurrentUser()).thenReturn(user);
        ReflectionTestUtils.setField(service, "appUserService", appUserService);
        ReflectionTestUtils.setField(service, "labelRepository", labelRepository);
        ReflectionTestUtils.setField(service, "labelMapper", labelMapper);
    }

    @Test
    void getAllLabels_mapsToDTOs() {
        Label label1 = new Label();
        label1.setId("l1");
        label1.setName("Food");
        label1.setAppUser(user);

        Label label2 = new Label();
        label2.setId("l2");
        label2.setName("Travel");
        label2.setAppUser(user);

        when(labelRepository.findByAppUser(user)).thenReturn(List.of(label1, label2));

        LabelDTO dto1 = new LabelDTO("l1", "Food");
        LabelDTO dto2 = new LabelDTO("l2", "Travel");
        when(labelMapper.toDTOList(List.of(label1, label2))).thenReturn(List.of(dto1, dto2));

        List<LabelDTO> result = service.getAllLabels();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(LabelDTO::name).containsExactly("Food", "Travel");
    }

    @Test
    void searchLabels_returnsMatchingLabels() {
        Label label1 = new Label();
        label1.setId("l1");
        label1.setName("Food");
        label1.setAppUser(user);

        Pageable pageable = PageRequest.of(0, 10);
        when(labelRepository.findByAppUserAndNameContainingIgnoreCase(user, "food", pageable))
            .thenReturn(List.of(label1));

        LabelDTO dto1 = new LabelDTO("l1", "Food");
        when(labelMapper.toDTOList(List.of(label1))).thenReturn(List.of(dto1));

        List<LabelDTO> result = service.searchLabels("food");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Food");
    }

    @Test
    void searchLabels_emptyQuery_returnsAllLabels() {
        Label label1 = new Label();
        label1.setId("l1");
        label1.setName("Food");
        label1.setAppUser(user);

        when(labelRepository.findByAppUser(user)).thenReturn(List.of(label1));

        LabelDTO dto1 = new LabelDTO("l1", "Food");
        when(labelMapper.toDTOList(List.of(label1))).thenReturn(List.of(dto1));

        List<LabelDTO> result = service.searchLabels("");

        assertThat(result).hasSize(1);
    }

    @Test
    void findOrCreateLabel_existingLabel_returnsExisting() {
        Label existingLabel = new Label();
        existingLabel.setId("l1");
        existingLabel.setName("Food");
        existingLabel.setAppUser(user);

        when(labelRepository.findByAppUserAndNameIgnoreCase(user, "Food"))
            .thenReturn(Optional.of(existingLabel));

        Label result = service.findOrCreateLabel("Food");

        assertThat(result).isEqualTo(existingLabel);
        assertThat(result.getId()).isEqualTo("l1");
        verify(labelRepository).findByAppUserAndNameIgnoreCase(user, "Food");
    }

    @Test
    void findOrCreateLabel_newLabel_createsAndReturns() {
        when(labelRepository.findByAppUserAndNameIgnoreCase(user, "NewLabel"))
            .thenReturn(Optional.empty());

        Label newLabel = new Label();
        newLabel.setId("l2");
        newLabel.setName("NewLabel");
        newLabel.setAppUser(user);

        when(labelRepository.save(any(Label.class))).thenAnswer(invocation -> {
            Label label = invocation.getArgument(0);
            label.setId("l2");
            return label;
        });

        Label result = service.findOrCreateLabel("NewLabel");

        assertThat(result.getName()).isEqualTo("NewLabel");
        assertThat(result.getAppUser()).isEqualTo(user);
        verify(labelRepository).save(any(Label.class));
    }

    @Test
    void findOrCreateLabel_trimmedName_handlesCorrectly() {
        Label existingLabel = new Label();
        existingLabel.setId("l1");
        existingLabel.setName("Food");
        existingLabel.setAppUser(user);

        when(labelRepository.findByAppUserAndNameIgnoreCase(user, "Food"))
            .thenReturn(Optional.of(existingLabel));

        Label result = service.findOrCreateLabel("  Food  ");

        assertThat(result.getName()).isEqualTo("Food");
    }

    @Test
    void createLabel_newLabel_createsAndReturnsDTO() {
        LabelDTO inputDTO = new LabelDTO(null, "NewLabel");
        Label newLabel = new Label();
        newLabel.setId("l1");
        newLabel.setName("NewLabel");
        newLabel.setAppUser(user);

        when(labelRepository.findByAppUserAndNameIgnoreCase(user, "NewLabel"))
            .thenReturn(Optional.empty());
        when(labelRepository.save(any(Label.class))).thenAnswer(invocation -> {
            Label label = invocation.getArgument(0);
            label.setId("l1");
            return label;
        });

        LabelDTO expectedDTO = new LabelDTO("l1", "NewLabel");
        when(labelMapper.toDTO(any(Label.class))).thenReturn(expectedDTO);

        LabelDTO result = service.createLabel(inputDTO);

        assertThat(result.name()).isEqualTo("NewLabel");
        assertThat(result.id()).isEqualTo("l1");
    }
}

