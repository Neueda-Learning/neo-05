// package com.neobank.module.service;

// import static org.assertj.core.api.Assertions.assertThat;
// import static org.assertj.core.api.Assertions.assertThatThrownBy;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.Mockito.when;

// import com.neobank.module.dto.CreditPolicyRequest;
// import com.neobank.module.dto.CreditPolicyView;
// import com.neobank.module.dto.ProductTermDTO;
// import com.neobank.module.model.CreditConfig;
// import com.neobank.module.repository.CreditConfigRepository;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import java.math.BigDecimal;
// import java.time.Instant;
// import java.util.List;
// import java.util.NoSuchElementException;
// import java.util.Optional;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.ArgumentCaptor;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;

// /**
//  * UC06: Credit policy versioning and validation.
//  * A version is the WHOLE config: all three products' terms plus dtiLimit, roundingStep, sampleEvery.
//  * current = MAX(version).
//  */
// @ExtendWith(MockitoExtension.class)
// class CreditPolicyServiceTest {

//     @Mock
//     private CreditConfigRepository repository;

//     private CreditPolicyService service;
//     private ObjectMapper mapper;

//     @BeforeEach
//     void setUp() {
//         mapper = new ObjectMapper();
//         service = new CreditPolicyService(repository, mapper);
//     }

//     @Test
//     void getCurrentPolicyReturnsHighestVersion() {
//         CreditConfig v1 = new CreditConfig(
//                 1,
//                 """
//                 [{"productCode":"CREDIT_CARD_REWARDS","minIncome":24000,"maxLimit":5000,"apr":18.9},\
//                 {"productCode":"CREDIT_CARD_LOW_RATE","minIncome":18000,"maxLimit":3000,"apr":12.9},\
//                 {"productCode":"CREDIT_CARD_STUDENT","minIncome":12000,"maxLimit":1500,"apr":22.9}]\
//                 """,
//                 new BigDecimal("0.45"),
//                 new BigDecimal("100"),
//                 10,
//                 Instant.now()
//         );

//         when(repository.findTopByOrderByVersionDesc()).thenReturn(Optional.of(v1));

//         CreditPolicyView current = service.getCurrentPolicy();

//         assertThat(current.version()).isEqualTo(1);
//         assertThat(current.dtiLimit()).isEqualTo(new BigDecimal("0.45"));
//         assertThat(current.sampleEvery()).isEqualTo(10);
//         assertThat(current.productTerms()).hasSize(3);
//         assertThat(current.productTerms().get(0).productCode()).isEqualTo("CREDIT_CARD_REWARDS");
//     }

//     @Test
//     void getCurrentPolicyThrowsWhenNoVersionExists() {
//         when(repository.findTopByOrderByVersionDesc()).thenReturn(Optional.empty());

//         assertThatThrownBy(() -> service.getCurrentPolicy())
//                 .isInstanceOf(NoSuchElementException.class)
//                 .hasMessageContaining("no credit policy version exists");
//     }

//     @Test
//     void createVersionIncrementsVersionNumber() {
//         CreditConfig v1 = new CreditConfig(
//                 1,
//                 "[]",
//                 new BigDecimal("0.45"),
//                 new BigDecimal("100"),
//                 10,
//                 Instant.now()
//         );

//         when(repository.findTopByOrderByVersionDesc()).thenReturn(Optional.of(v1));
//         when(repository.save(any(CreditConfig.class))).thenAnswer(inv -> inv.getArgument(0));

//         CreditPolicyRequest request = new CreditPolicyRequest(
//                 new BigDecimal("0.50"),
//                 150,
//                 15,
//                 List.of(
//                         new ProductTermDTO("CREDIT_CARD_REWARDS", 24000, 5000, new BigDecimal("18.9")),
//                         new ProductTermDTO("CREDIT_CARD_LOW_RATE", 18000, 3000, new BigDecimal("12.9")),
//                         new ProductTermDTO("CREDIT_CARD_STUDENT", 12000, 1500, new BigDecimal("22.9"))
//                 )
//         );

//         CreditPolicyView created = service.createVersion(request);

//         assertThat(created.version()).isEqualTo(2);
//         assertThat(created.dtiLimit()).isEqualTo(new BigDecimal("0.50"));
//         assertThat(created.roundingStep()).isEqualTo(150);
//     }

//     @Test
//     void rejectsMissingCatalogueProduct() {
//         CreditPolicyRequest request = new CreditPolicyRequest(
//                 new BigDecimal("0.45"),
//                 100,
//                 10,
//                 List.of(
//                         // Only two products — missing CREDIT_CARD_STUDENT
//                         new ProductTermDTO("CREDIT_CARD_REWARDS", 24000, 5000, new BigDecimal("18.9")),
//                         new ProductTermDTO("CREDIT_CARD_LOW_RATE", 18000, 3000, new BigDecimal("12.9"))
//                 )
//         );

//         assertThatThrownBy(() -> service.createVersion(request))
//                 .isInstanceOf(IllegalArgumentException.class)
//                 .hasMessageContaining("missing products");
//     }

//     @Test
//     void rejectsDtiLimitOutOfRange() {
//         assertThatThrownBy(() -> new CreditPolicyRequest(
//                 new BigDecimal("1.5"), // > 1, invalid
//                 100,
//                 10,
//                 List.of(
//                         new ProductTermDTO("CREDIT_CARD_REWARDS", 24000, 5000, new BigDecimal("18.9")),
//                         new ProductTermDTO("CREDIT_CARD_LOW_RATE", 18000, 3000, new BigDecimal("12.9")),
//                         new ProductTermDTO("CREDIT_CARD_STUDENT", 12000, 1500, new BigDecimal("22.9"))
//                 )
//         )).isInstanceOf(Exception.class);
//     }

//     @Test
//     void rejectsNegativeMinIncome() {
//         CreditPolicyRequest request = new CreditPolicyRequest(
//                 new BigDecimal("0.45"),
//                 100,
//                 10,
//                 List.of(
//                         new ProductTermDTO("CREDIT_CARD_REWARDS", -1000, 5000, new BigDecimal("18.9")),
//                         new ProductTermDTO("CREDIT_CARD_LOW_RATE", 18000, 3000, new BigDecimal("12.9")),
//                         new ProductTermDTO("CREDIT_CARD_STUDENT", 12000, 1500, new BigDecimal("22.9"))
//                 )
//         );

//         assertThatThrownBy(() -> service.createVersion(request))
//                 .isInstanceOf(IllegalArgumentException.class)
//                 .hasMessageContaining("minIncome");
//     }

//     @Test
//     void rejectsZeroOrNegativeMaxLimit() {
//         CreditPolicyRequest request = new CreditPolicyRequest(
//                 new BigDecimal("0.45"),
//                 100,
//                 10,
//                 List.of(
//                         new ProductTermDTO("CREDIT_CARD_REWARDS", 24000, 0, new BigDecimal("18.9")),
//                         new ProductTermDTO("CREDIT_CARD_LOW_RATE", 18000, 3000, new BigDecimal("12.9")),
//                         new ProductTermDTO("CREDIT_CARD_STUDENT", 12000, 1500, new BigDecimal("22.9"))
//                 )
//         );

//         assertThatThrownBy(() -> service.createVersion(request))
//                 .isInstanceOf(IllegalArgumentException.class)
//                 .hasMessageContaining("maxLimit");
//     }

//     @Test
//     void rejectsAprWithWrongDecimalPlaces() {
//         CreditPolicyRequest request = new CreditPolicyRequest(
//                 new BigDecimal("0.45"),
//                 100,
//                 10,
//                 List.of(
//                         new ProductTermDTO("CREDIT_CARD_REWARDS", 24000, 5000, new BigDecimal("18.95")), // two decimals
//                         new ProductTermDTO("CREDIT_CARD_LOW_RATE", 18000, 3000, new BigDecimal("12.9")),
//                         new ProductTermDTO("CREDIT_CARD_STUDENT", 12000, 1500, new BigDecimal("22.9"))
//                 )
//         );

//         assertThatThrownBy(() -> service.createVersion(request))
//                 .isInstanceOf(IllegalArgumentException.class)
//                 .hasMessageContaining("apr must have exactly one decimal place");
//     }

//     @Test
//     void acceptsValidPolicyWithAllConstraintsSatisfied() {
//         when(repository.findTopByOrderByVersionDesc()).thenReturn(Optional.empty());
//         when(repository.save(any(CreditConfig.class))).thenAnswer(inv -> {
//             CreditConfig arg = inv.getArgument(0);
//             return new CreditConfig(
//                     arg.getVersion(),
//                     arg.getProductTerms(),
//                     arg.getDtiLimit(),
//                     arg.getRoundingStep(),
//                     arg.getSampleEvery(),
//                     Instant.now()
//             );
//         });

//         CreditPolicyRequest request = new CreditPolicyRequest(
//                 new BigDecimal("0.45"),
//                 100,
//                 10,
//                 List.of(
//                         new ProductTermDTO("CREDIT_CARD_REWARDS", 24000, 5000, new BigDecimal("18.9")),
//                         new ProductTermDTO("CREDIT_CARD_LOW_RATE", 18000, 3000, new BigDecimal("12.9")),
//                         new ProductTermDTO("CREDIT_CARD_STUDENT", 12000, 1500, new BigDecimal("22.9"))
//                 )
//         );

//         CreditPolicyView created = service.createVersion(request);

//         assertThat(created.version()).isEqualTo(1); // first version
//         assertThat(created.dtiLimit()).isEqualTo(new BigDecimal("0.45"));
//         assertThat(created.roundingStep()).isEqualTo(100);
//         assertThat(created.sampleEvery()).isEqualTo(10);
//         assertThat(created.productTerms()).hasSize(3);
//     }
// }
