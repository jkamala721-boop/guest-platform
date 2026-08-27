package com.guest_platform.service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guest_platform.dto.*;
import com.guest_platform.entity.*;
import com.guest_platform.exception.LifecycleNotFoundException;
import com.guest_platform.repository.*;

@Service
public class AdminHostOperationsService {
    private final HostRepository hosts;
    private final HostVerificationRepository verifications;
    private final HostAgreementVersionRepository agreementVersions;
    private final HostAgreementAcceptanceRepository agreementAcceptances;
    private final PropertyRepository properties;
    private final HostPayoutSettingsRepository payouts;
    private final BookingRepository bookings;

    public AdminHostOperationsService(HostRepository hosts,HostVerificationRepository verifications,
            HostAgreementVersionRepository agreementVersions,HostAgreementAcceptanceRepository agreementAcceptances,
            PropertyRepository properties,HostPayoutSettingsRepository payouts,BookingRepository bookings) {
        this.hosts=hosts;this.verifications=verifications;this.agreementVersions=agreementVersions;
        this.agreementAcceptances=agreementAcceptances;this.properties=properties;this.payouts=payouts;this.bookings=bookings;
    }

    @Transactional(readOnly=true)
    public AdminHostPageResponse list(String q,HostAccountStatus accountStatus,
            HostVerificationStatus verificationStatus,int page,int size) {
        if(page<0)throw new IllegalArgumentException("Page must not be negative");
        if(size<1||size>100)throw new IllegalArgumentException("Size must be between 1 and 100");
        String search=q==null||q.isBlank()?null:"%"+q.trim().toLowerCase(Locale.ROOT)+"%";
        Pageable pageable=PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.asc("id")));
        Page<Host> result=hosts.searchAdminHosts(search,accountStatus,verificationStatus,pageable);
        List<Host> pageHosts=result.getContent();
        if(pageHosts.isEmpty())return new AdminHostPageResponse(List.of(),page,size,result.getTotalElements(),result.getTotalPages());
        Set<UUID> ids=pageHosts.stream().map(Host::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID,HostVerification> verificationByHost=verifications.findAllByHostIdIn(ids).stream()
                .collect(Collectors.toMap(v->v.getHost().getId(),Function.identity()));
        Map<UUID,PropertyRepository.HostPropertySummary> propertyByHost=properties.summarizeByHostIds(ids).stream()
                .collect(Collectors.toMap(PropertyRepository.HostPropertySummary::getHostId,Function.identity()));
        Map<UUID,HostPayoutSettings> payoutByHost=payouts.findAllByHostIdIn(ids).stream()
                .collect(Collectors.toMap(HostPayoutSettings::getHostId,Function.identity()));
        Map<UUID,BookingAggregate> bookingByHost=bookingAggregates(ids);
        HostAgreementVersionRepository.CurrentAgreementSummary currentAgreement=agreementVersions
                .findFirstByActiveTrueOrderByEffectiveAtDesc(HostAgreementVersionRepository.CurrentAgreementSummary.class).orElse(null);
        Map<UUID,Instant> acceptanceByHost=currentAgreement==null?Map.of():agreementAcceptances
                .summarizeCurrentForHosts(currentAgreement.getId(),ids).stream().collect(Collectors.toMap(
                        HostAgreementAcceptanceRepository.HostAgreementAcceptanceSummary::getHostId,
                        HostAgreementAcceptanceRepository.HostAgreementAcceptanceSummary::getAcceptedAt));
        List<AdminHostListItem> items=pageHosts.stream().map(host->{
            HostVerification verification=verificationByHost.get(host.getId());
            PropertyRepository.HostPropertySummary property=propertyByHost.get(host.getId());
            HostPayoutSettings payout=payoutByHost.get(host.getId());
            BookingAggregate booking=bookingByHost.get(host.getId());
            Instant acceptedAt=acceptanceByHost.get(host.getId());
            Instant last=latest(host.getUpdatedAt(),verification==null?null:verification.getReviewedAt(),
                    verification==null?null:verification.getSubmittedAt(),property==null?null:property.getLastActivityAt(),
                    payout==null?null:payout.getUpdatedAt(),booking==null?null:booking.lastActivityAt(),acceptedAt);
            return new AdminHostListItem(host.getId(),host.getEmail(),host.getFullName(),host.getPhone(),
                    host.getAccountStatus(),host.getAccountStatus()==HostAccountStatus.SUSPENDED?host.getAccountSuspensionReason():null,
                    verification==null?HostVerificationStatus.UNVERIFIED:verification.getStatus(),
                    verification==null?null:verification.getVerificationType(),verification==null?null:verification.getCountryCode(),
                    acceptedAt!=null,currentAgreement==null?null:currentAgreement.getVersion(),
                    property==null?0:property.getTotalCount(),property==null?0:property.getActiveCount(),
                    payout!=null&&payout.getStatus()==PayoutSettingsStatus.CONFIGURED,host.getCreatedAt(),last);
        }).toList();
        return new AdminHostPageResponse(items,page,size,result.getTotalElements(),result.getTotalPages());
    }

    @Transactional(readOnly=true)
    public AdminHostDetailResponse detail(UUID hostId) {
        Host host=hosts.findById(hostId).orElseThrow(()->new LifecycleNotFoundException(
                "ADMIN_HOST_NOT_FOUND","Host was not found."));
        HostVerification verification=verifications.findByHostId(hostId).orElse(null);
        HostAgreementVersionRepository.CurrentAgreementSummary current=agreementVersions
                .findFirstByActiveTrueOrderByEffectiveAtDesc(HostAgreementVersionRepository.CurrentAgreementSummary.class).orElse(null);
        HostAgreementAcceptance acceptance=current==null?null:agreementAcceptances
                .findByHostIdAndAgreementVersionId(hostId,current.getId()).orElse(null);
        List<PropertyRepository.AdminPropertyView> hostProperties=properties.findAdminViewByHostId(hostId);
        long activeProperties=hostProperties.stream().filter(PropertyRepository.AdminPropertyView::isActive).count();
        HostPayoutSettings payout=payouts.findByHostId(hostId).orElse(null);
        BookingAggregate booking=bookingAggregates(Set.of(hostId)).getOrDefault(hostId,BookingAggregate.empty());
        return new AdminHostDetailResponse(host.getId(),host.getEmail(),host.getFullName(),host.getPhone(),
                host.getAccountStatus(),host.getAccountSuspensionReason(),host.getCreatedAt(),verification(verification),
                new AdminHostDetailResponse.Agreement(current==null?null:current.getVersion(),acceptance!=null,
                        acceptance==null?null:acceptance.getAcceptedAt()),
                new AdminHostDetailResponse.Properties(hostProperties.size(),activeProperties,hostProperties.stream()
                        .map(property->new AdminHostDetailResponse.PropertyItem(property.getPropertyId(),property.getName(),
                                property.getPropertyType(),property.isActive(),property.getAddress(),property.getMapsUrl(),
                                property.getCreatedAt())).toList()),payout(payout),booking.response());
    }

    private AdminHostDetailResponse.Verification verification(HostVerification verification) {
        if(verification==null)return new AdminHostDetailResponse.Verification(HostVerificationStatus.UNVERIFIED,null,null,
                null,null,null,null,null,null,null);
        return new AdminHostDetailResponse.Verification(verification.getStatus(),verification.getSubmittedAt(),
                verification.getReviewedAt(),verification.getStatus()==HostVerificationStatus.REJECTED
                        ?verification.getRejectionReason():null,verification.getLegalName(),
                verification.getVerificationType(),verification.getIdType(),verification.getIdNumberLast4(),
                verification.getCountryCode(),verification.getPhone());
    }

    private AdminHostDetailResponse.Payout payout(HostPayoutSettings payout) {
        if(payout==null)return new AdminHostDetailResponse.Payout(false,null,null,null,null);
        String last4=payout.getPayoutMethod()==PayoutMethod.MPESA?payout.getMpesaPhoneLast4():payout.getAccountNumberLast4();
        return new AdminHostDetailResponse.Payout(payout.getStatus()==PayoutSettingsStatus.CONFIGURED,"PAYSTACK",
                payout.getPayoutMethod(),payout.getStatus(),last4);
    }

    private Map<UUID,BookingAggregate> bookingAggregates(Collection<UUID> hostIds) {
        Map<UUID,BookingAggregate> result=new HashMap<>();
        for(BookingRepository.HostBookingStatusSummary row:bookings.summarizeByHostIds(hostIds)) {
            result.computeIfAbsent(row.getHostId(),ignored->BookingAggregate.empty())
                    .add(row.getStatus(),row.getStatusCount(),row.getLastActivityAt());
        }
        return result;
    }

    private static Instant latest(Instant... values) {
        return Arrays.stream(values).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }

    private static final class BookingAggregate {
        private final EnumMap<BookingStatus,Long> counts=new EnumMap<>(BookingStatus.class);
        private long total;
        private Instant lastActivityAt;
        private BookingAggregate(){for(BookingStatus status:BookingStatus.values())counts.put(status,0L);}
        static BookingAggregate empty(){return new BookingAggregate();}
        void add(BookingStatus status,long count,Instant activity){counts.put(status,count);total+=count;lastActivityAt=latest(lastActivityAt,activity);}
        Instant lastActivityAt(){return lastActivityAt;}
        AdminHostDetailResponse.BookingActivity response(){return new AdminHostDetailResponse.BookingActivity(total,
                Collections.unmodifiableMap(counts),counts.get(BookingStatus.CONFIRMED),counts.get(BookingStatus.CANCELLED),lastActivityAt);}
    }
}
