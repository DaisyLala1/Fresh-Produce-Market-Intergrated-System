package com.freshproduce.pos.service;

import com.freshproduce.pos.domain.Commission;
import com.freshproduce.pos.domain.CommissionRate;
import com.freshproduce.pos.domain.Sale;
import com.freshproduce.pos.domain.User;
import com.freshproduce.pos.dto.Responses.CommissionRateDto;
import com.freshproduce.pos.exception.BusinessException;
import com.freshproduce.pos.repository.CommissionRateRepository;
import com.freshproduce.pos.repository.CommissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/** Configurable commission rates (FR-10) and commission calculation (FR-04). */
@Service
public class CommissionService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CommissionRateRepository rateRepository;
    private final CommissionRepository commissionRepository;

    public CommissionService(CommissionRateRepository rateRepository, CommissionRepository commissionRepository) {
        this.rateRepository = rateRepository;
        this.commissionRepository = commissionRepository;
    }

    /** commission = amount × rate ÷ 100, rounded to cents. R1,000 at 5% = R50.00. */
    public static BigDecimal calculate(BigDecimal amount, BigDecimal ratePercent) {
        return amount.multiply(ratePercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public CommissionRate currentRate() {
        return rateRepository.findFirstByActiveTrueOrderByEffectiveFromDesc()
                .orElseThrow(() -> new BusinessException(
                        "No commission rate has been configured. An Administrator must set one first."));
    }

    /** Records the agent's commission for a sale using the rate active at the time of the sale. */
    @Transactional
    public Commission recordCommission(Sale sale) {
        CommissionRate rate = currentRate();
        Commission commission = new Commission();
        commission.setSale(sale);
        commission.setAgent(sale.getAgent());
        commission.setCommissionRate(rate.getRatePercent());
        commission.setCommissionAmount(calculate(sale.getTotalAmount(), rate.getRatePercent()));
        commission.setCreatedAt(sale.getSaleDate());
        return commissionRepository.save(commission);
    }

    /** Replaces the active rate. Earlier sales keep the rate stored on their own commission record. */
    @Transactional
    public CommissionRateDto updateRate(BigDecimal ratePercent, User administrator) {
        rateRepository.findByActiveTrue().forEach(r -> r.setActive(false));
        CommissionRate rate = new CommissionRate(ratePercent.setScale(2, RoundingMode.HALF_UP), administrator);
        rate.setEffectiveFrom(LocalDateTime.now());
        return CommissionRateDto.from(rateRepository.save(rate));
    }

    @Transactional(readOnly = true)
    public List<CommissionRateDto> history() {
        return rateRepository.findAllByOrderByEffectiveFromDesc().stream().map(CommissionRateDto::from).toList();
    }
}
