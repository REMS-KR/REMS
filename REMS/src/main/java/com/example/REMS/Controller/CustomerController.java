package com.example.REMS.Controller;

import com.example.REMS.DTO.CustomerDTO;
import com.example.REMS.Service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    // 고객 등록
    @Operation(summary = "고객 등록 (중개사)")
    @PostMapping("/{uid}")
    public ResponseEntity<CustomerDTO> createCustomer(@PathVariable("uid") String uid,
                                                      @RequestBody CustomerDTO dto,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(customerService.createCustomer(uid, dto, userDetails));
    }

    // 내 고객 목록
    @Operation(summary = "내 고객 목록 (중개사)")
    @GetMapping("/{uid}")
    public ResponseEntity<List<CustomerDTO>> getMyCustomers(@PathVariable("uid") String uid,
                                                            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(customerService.getMyCustomers(uid, userDetails));
    }

    // 고객 수정
    @Operation(summary = "고객 수정 (중개사)")
    @PutMapping("/{uid}/{id}")
    public ResponseEntity<CustomerDTO> updateCustomer(@PathVariable("uid") String uid,
                                                      @PathVariable("id") Long id,
                                                      @RequestBody CustomerDTO dto,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(customerService.updateCustomer(uid, id, dto, userDetails));
    }

    // 고객 삭제
    @Operation(summary = "고객 삭제 (중개사)")
    @DeleteMapping("/{uid}/{id}")
    public ResponseEntity<CustomerDTO> deleteCustomer(@PathVariable("uid") String uid,
                                                      @PathVariable("id") Long id,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(customerService.deleteCustomer(uid, id, userDetails));
    }
}
