package com.nklmthr.finance.personal.controller;

import com.nklmthr.finance.personal.model.Institution;
import com.nklmthr.finance.personal.service.InstitutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/institutions")
public class InstitutionController {

    private final InstitutionService institutionService;

    @Autowired
    public InstitutionController(InstitutionService institutionService) {
        this.institutionService = institutionService;
    }

    @GetMapping
    public List<Institution> getAllInstitutions() {
        return institutionService.getAllInstitutions();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Institution> getInstitutionById(@PathVariable String id) {
        return institutionService.getInstitutionById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Institution> createInstitution(@RequestBody Institution institution) {
        return ResponseEntity.ok(institutionService.createInstitution(institution));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Institution> updateInstitution(@PathVariable String id, @RequestBody Institution institution) {
        return ResponseEntity.ok(institutionService.updateInstitution(id, institution));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInstitution(@PathVariable String id) {
        institutionService.deleteInstitution(id);
        return ResponseEntity.noContent().build();
    }
}
