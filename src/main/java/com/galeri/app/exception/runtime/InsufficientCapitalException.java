package com.galeri.app.exception.runtime;

import com.galeri.app.constant.ErrorMessageConstant;
import com.galeri.app.model.response.Shortfall;
import com.galeri.app.util.MoneyText;

import java.util.List;
import java.util.stream.Collectors;

// Ortak / konsinye aracta kisinin nakitteki sermayesi (sermaye - araclarda bagli) katkisina yetmiyor: kalanini cebinden eklemeli
// ya da dukkan sahibi borc vermeli. data: parasi yetmeyen her kisi ve eksik tutari (panel arac formunda borc secenegi gosterir).
public class InsufficientCapitalException extends ApplicationRuntimeException {
    public InsufficientCapitalException(List<Shortfall> shortfalls) {
        super(shortfalls.stream().map(s -> s.name() + " (" + MoneyText.tl(s.shortfallCents()) + " eksik)").collect(Collectors.joining(", "))
                        + ": nakitteki sermaye katkıya yetmiyor; katkının bir kısmını cepten ekleyin ya da dükkân sahibi borç versin",
                ErrorMessageConstant.INSUFFICIENT_CAPITAL, List.copyOf(shortfalls));
    }
}
