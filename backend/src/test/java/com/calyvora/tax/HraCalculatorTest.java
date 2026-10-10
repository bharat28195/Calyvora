package com.calyvora.tax;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The HRA exemption, worked by hand: the least of three amounts, every month. */
class HraCalculatorTest {

    private static BigDecimal rs(String v) {
        return new BigDecimal(v);
    }

    private static List<HraCalculator.Month> year(String basic, String hra, String rent, boolean metro, int months) {
        List<HraCalculator.Month> out = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            out.add(new HraCalculator.Month("m" + i, rs(basic), rs(hra), rs(rent), metro));
        }
        return out;
    }

    @Test
    @DisplayName("metro: rent less 10% of basic is the least")
    void metro_rent_is_least() {
        // Basic 50,000, HRA 25,000, rent 20,000, Mumbai.
        //   HRA 25,000; rent - 5,000 = 15,000; 50% of basic 25,000 -> 15,000 a month, 1,80,000 a year.
        HraCalculator.Result r = HraCalculator.compute(year("50000", "25000", "20000", true, 12));
        assertThat(r.exempt()).isEqualByComparingTo(rs("180000"));
        assertThat(r.received()).isEqualByComparingTo(rs("300000"));
    }

    @Test
    @DisplayName("non-metro: 40% of basic is the least")
    void non_metro_percentage_is_least() {
        // Basic 40,000, HRA 20,000, rent 30,000, Jaipur.
        //   20,000; 30,000 - 4,000 = 26,000; 40% = 16,000 -> 16,000 a month.
        HraCalculator.Result r = HraCalculator.compute(year("40000", "20000", "30000", false, 12));
        assertThat(r.exempt()).isEqualByComparingTo(rs("192000"));
    }

    @Test
    @DisplayName("the HRA received caps it")
    void hra_received_is_least() {
        // Basic 1,00,000, HRA 10,000, rent 60,000, Delhi: 10,000; 50,000; 50,000 -> 10,000.
        HraCalculator.Result r = HraCalculator.compute(year("100000", "10000", "60000", true, 12));
        assertThat(r.exempt()).isEqualByComparingTo(rs("120000"));
    }

    @Test
    @DisplayName("rent below 10% of basic exempts nothing")
    void tiny_rent() {
        HraCalculator.Result r = HraCalculator.compute(year("50000", "25000", "4000", true, 12));
        assertThat(r.exempt()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("a move mid-year is worked month by month, not averaged")
    void a_move_mid_year() {
        // Six months in Pune (now a metro) at 25,000; six in Indore at 15,000. Basic 40,000, HRA 20,000.
        //   Pune:  20,000; 25,000 - 4,000 = 21,000; 50% 20,000 -> 20,000 x 6 = 1,20,000
        //   Indore: 20,000; 15,000 - 4,000 = 11,000; 40% 16,000 -> 11,000 x 6 = 66,000
        List<HraCalculator.Month> months = new ArrayList<>(year("40000", "20000", "25000", HraCalculator.isMetro("Pune"), 6));
        months.addAll(year("40000", "20000", "15000", HraCalculator.isMetro("Indore"), 6));
        assertThat(HraCalculator.compute(months).exempt()).isEqualByComparingTo(rs("186000"));
    }

    @Test
    @DisplayName("eight metros from 2026-27, in any common spelling")
    void metros() {
        for (String c : new String[]{"Mumbai", "Bombay", "Delhi", "New Delhi", "Kolkata", "Chennai",
                "Bengaluru", "Bangalore", "Hyderabad", "Pune", "Ahmedabad", " pune "}) {
            assertThat(HraCalculator.isMetro(c)).as(c).isTrue();
        }
        for (String c : new String[]{"Jaipur", "Gurugram", "Noida", "Indore", "", null}) {
            assertThat(HraCalculator.isMetro(c)).as(String.valueOf(c)).isFalse();
        }
    }
}
