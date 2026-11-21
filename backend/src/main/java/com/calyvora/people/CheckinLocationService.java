package com.calyvora.people;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.company.CompanySettings;
import com.calyvora.company.CompanySettingsRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Location at check-in (PD-81). The phone — or a browser — sends where it is when someone checks in
 * or out; this decides what to keep and whether to let it through.
 *
 * <ul>
 *   <li><b>OFF</b> — location is never asked for and never stored, even if a client sends it.</li>
 *   <li><b>RECORD</b> — stored with the nearest office and distance, shown to managers, never refused.</li>
 *   <li><b>ENFORCE</b> — a check-in or check-out outside every office is refused. A company with no
 *       offices set up yet behaves as RECORD, so switching it on can never lock everybody out.</li>
 * </ul>
 *
 * <p>Only those two moments are recorded. Nobody is tracked through the day.
 */
@Service
public class CheckinLocationService {

    public enum Mode { OFF, RECORD, ENFORCE }

    /** What a client sends. All optional; accuracy is the phone's own error radius in metres. */
    public record Fix(Double latitude, Double longitude, Double accuracy) {}

    /** What gets stored on the punch. {@code office} is null when outside every office. */
    public record Placed(double latitude, double longitude, Integer accuracyM, String office, Integer distanceM) {}

    /** GPS is never exact: up to this much of the phone's own error counts in the person's favour. */
    static final int MAX_ACCURACY_SLACK_M = 100;

    private final CompanySettingsRepository settingsRepository;
    private final OfficeLocationRepository officeRepository;

    public CheckinLocationService(CompanySettingsRepository settingsRepository,
                                  OfficeLocationRepository officeRepository) {
        this.settingsRepository = settingsRepository;
        this.officeRepository = officeRepository;
    }

    public Mode mode(UUID companyId) {
        String raw = settingsRepository.findById(companyId).map(CompanySettings::getCheckinLocationMode).orElse("OFF");
        try {
            return Mode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return Mode.OFF;
        }
    }

    /**
     * Decide what to store for this check-in or check-out, refusing it when the company enforces
     * location and the person is outside every office.
     *
     * @return null when nothing should be stored
     */
    public Placed place(UUID companyId, Fix fix) {
        Mode mode = mode(companyId);
        if (mode == Mode.OFF) {
            return null;
        }
        List<OfficeLocation> offices = officeRepository.findByCompanyIdOrderByCreatedAtAsc(companyId);
        boolean enforcing = mode == Mode.ENFORCE && !offices.isEmpty();
        if (!valid(fix)) {
            if (enforcing) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Turn on location and allow Orbit to use it — your company checks you in from the office");
            }
            return null;
        }

        Integer accuracy = fix.accuracy() == null ? null : (int) Math.round(Math.max(0, fix.accuracy()));
        int slack = accuracy == null ? 0 : Math.min(accuracy, MAX_ACCURACY_SLACK_M);

        OfficeLocation nearest = null;
        double nearestM = Double.MAX_VALUE;
        for (OfficeLocation o : offices) {
            double d = distanceM(fix.latitude(), fix.longitude(), o.getLatitude(), o.getLongitude());
            if (d < nearestM) {
                nearestM = d;
                nearest = o;
            }
        }
        boolean inside = nearest != null && nearestM <= nearest.getRadiusM() + slack;
        if (enforcing && !inside) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, String.format(
                    "You're about %s from %s. Check in when you're at the office.",
                    human(nearestM), nearest.getName()));
        }
        return new Placed(fix.latitude(), fix.longitude(), accuracy,
                inside ? nearest.getName() : null,
                nearest == null ? null : (int) Math.round(nearestM));
    }

    private static boolean valid(Fix f) {
        return f != null && f.latitude() != null && f.longitude() != null
                && f.latitude() >= -90 && f.latitude() <= 90
                && f.longitude() >= -180 && f.longitude() <= 180
                && !(f.latitude() == 0 && f.longitude() == 0);
    }

    private static String human(double m) {
        return m >= 1000 ? String.format("%.1f km", m / 1000) : Math.round(m) + " m";
    }

    /** Great-circle distance in metres (haversine). Plenty accurate at office scale. */
    static double distanceM(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
