import SwiftUI

public struct PlanAccessView: View {
    @Environment(EntitlementManager.self) private var entitlementManager
    @Environment(StoreKitBillingService.self) private var billingService
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    public init() {}

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: OAKSpacing.section) {
                currentPlanHero
                planComparisonSurface
                storePurchaseSurface
            }
            .frame(maxWidth: 760)
            .padding(.horizontal, OAKSpacing.lg)
            .padding(.vertical, OAKSpacing.sm)
            .frame(maxWidth: .infinity)
        }
        .background { Color.clear.oakBackground() }
        .navigationTitle("plan_access_title".localized)
        .task {
            DiagnosticsReporter.event(
                "plan_access_view",
                fields: ["plan": entitlementManager.snapshot.plan.rawValue]
            )
            await billingService.refresh()
        }
    }

    private var currentPlanHero: some View {
        VStack(alignment: .leading, spacing: OAKSpacing.sm) {
            Text("plan_access_current_plan".localized)
                .font(.caption.weight(.semibold))
                .oakSecondaryText()
            Text(planTitle(entitlementManager.snapshot.plan))
                .font(.oakDisplay(size: 34))
            Text(planSubtitle(entitlementManager.snapshot.plan))
                .font(.subheadline)
                .oakSecondaryText()
        }
        .padding(OAKSpacing.xl)
        .frame(maxWidth: .infinity, alignment: .leading)
        .oakCardStyle(.paper, cornerRadius: OAKRadius.lg)
    }

    private var planComparisonSurface: some View {
        VStack(spacing: 0) {
            ForEach([CommercialPlan.free, .pro, .coach], id: \.self) { plan in
                planSection(plan)
                if plan != .coach {
                    Divider().overlay(OAKPalette.divider(for: colorScheme))
                }
            }
        }
        .padding(.horizontal, OAKSpacing.lg)
        .background(
            OAKPalette.surface(for: colorScheme),
            in: RoundedRectangle(cornerRadius: OAKRadius.md, style: .continuous)
        )
        .overlay {
            RoundedRectangle(cornerRadius: OAKRadius.md, style: .continuous)
                .stroke(OAKPalette.divider(for: colorScheme), lineWidth: 0.75)
        }
    }

    private func planSection(_ plan: CommercialPlan) -> some View {
        VStack(alignment: .leading, spacing: OAKSpacing.sm) {
            planHeader(plan)
            ForEach(planFeatureKeys(plan), id: \.self) { key in
                featureRow(key.localized)
            }
        }
        .padding(.vertical, OAKSpacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func planHeader(_ plan: CommercialPlan) -> some View {
        if dynamicTypeSize >= .accessibility1 {
            VStack(alignment: .leading, spacing: OAKSpacing.xs) {
                planHeading(plan)
                if entitlementManager.snapshot.plan == plan { currentPlanBadge }
            }
        } else {
            HStack(alignment: .top) {
                planHeading(plan)
                Spacer()
                if entitlementManager.snapshot.plan == plan { currentPlanBadge }
            }
        }
    }

    private func planHeading(_ plan: CommercialPlan) -> some View {
        VStack(alignment: .leading, spacing: OAKSpacing.xs) {
            Text(planTitle(plan)).font(.title3.weight(.semibold))
            Text(planSubtitle(plan)).font(.subheadline).oakSecondaryText()
        }
    }

    private var currentPlanBadge: some View {
        Text("plan_access_current_badge".localized)
            .font(.caption.weight(.semibold))
            .foregroundStyle(OAKPalette.accent)
    }

    private func featureRow(_ label: String) -> some View {
        HStack(spacing: OAKSpacing.sm) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(OAKPalette.taken(for: colorScheme))
            Text(label).font(.subheadline)
        }
    }

    private var storePurchaseSurface: some View {
        VStack(alignment: .leading, spacing: OAKSpacing.md) {
            Text("billing_store_products".localized)
                .font(.title3.weight(.semibold))
            if billingService.isLoading && billingService.products.isEmpty {
                ProgressView().frame(maxWidth: .infinity)
            } else if billingService.products.isEmpty {
                storePreview
            }
            ForEach(billingService.products, id: \.productId) { product in
                purchaseRow(product)
                Divider().overlay(OAKPalette.divider(for: colorScheme))
            }
            restoreBlock
        }
        .padding(OAKSpacing.lg)
        .background(
            OAKPalette.mutedSurface(for: colorScheme),
            in: RoundedRectangle(cornerRadius: OAKRadius.md, style: .continuous)
        )
    }

    private var storePreview: some View {
        VStack(alignment: .leading, spacing: OAKSpacing.xs) {
            Text("plan_preview_title".localized).font(.subheadline.weight(.semibold))
            Text("plan_preview_body".localized).font(.caption).oakSecondaryText()
        }
    }

    @ViewBuilder
    private func purchaseRow(_ product: StoreProductViewState) -> some View {
        if dynamicTypeSize >= .accessibility1 {
            VStack(alignment: .leading, spacing: OAKSpacing.sm) {
                purchaseLabel(product)
                purchaseButton(product)
            }
            .padding(.vertical, OAKSpacing.sm)
        } else {
            HStack(spacing: OAKSpacing.md) {
                purchaseLabel(product)
                Spacer()
                purchaseButton(product)
            }
            .padding(.vertical, OAKSpacing.sm)
        }
    }

    private func purchaseLabel(_ product: StoreProductViewState) -> some View {
        VStack(alignment: .leading, spacing: OAKSpacing.xs) {
            Text(product.displayName).font(.headline)
            Text(product.displayPrice).font(.subheadline).oakSecondaryText()
        }
    }

    private func purchaseButton(_ product: StoreProductViewState) -> some View {
        Button("billing_buy".localized) {
            DiagnosticsReporter.event(
                "billing_purchase_started",
                fields: CommercialTelemetryFields.product(product.productId, source: "app_store")
            )
            Task { await billingService.purchase(productId: product.productId) }
        }
        .buttonStyle(.borderedProminent)
        .disabled(billingService.purchasingProductId != nil)
    }

    private var restoreBlock: some View {
        VStack(alignment: .leading, spacing: OAKSpacing.sm) {
            Button("billing_restore".localized) {
                DiagnosticsReporter.event(
                    "billing_restore_started",
                    fields: ["source": "app_store"]
                )
                Task { await billingService.restorePurchases() }
            }
            .buttonStyle(.borderedProminent)
            .frame(maxWidth: .infinity, alignment: .leading)
            if let notice = billingService.notice {
                Text(noticeText(notice)).font(.footnote).oakSecondaryText()
            }
            Text("billing_store_authoritative_note".localized)
                .font(.footnote)
                .oakSecondaryText()
        }
    }

    private func noticeText(_ notice: BillingNotice) -> String {
        switch notice {
        case .purchaseCompleted: return "billing_purchase_completed".localized
        case .purchasePending: return "billing_purchase_pending".localized
        case .purchaseCancelled: return "billing_purchase_cancelled".localized
        case .restoreCompleted: return "billing_restore_completed".localized
        case .verificationFailed: return "billing_verification_failed".localized
        case .storeUnavailable: return "billing_store_unavailable".localized
        }
    }

    private func planTitle(_ plan: CommercialPlan) -> String {
        switch plan {
        case .free: return "plan_free_title".localized
        case .pro: return "plan_pro_title".localized
        case .coach: return "plan_coach_title".localized
        }
    }

    private func planSubtitle(_ plan: CommercialPlan) -> String {
        switch plan {
        case .free: return "plan_free_subtitle".localized
        case .pro: return "plan_pro_subtitle".localized
        case .coach: return "plan_coach_subtitle".localized
        }
    }

    private func planFeatureKeys(_ plan: CommercialPlan) -> [String] {
        switch plan {
        case .free:
            return ["plan_feature_basic_tracking", "plan_feature_reminders", "plan_feature_recent_history"]
        case .pro:
            return [
                "plan_feature_advanced_cycles",
                "plan_feature_unlimited_history",
                "plan_feature_adherence_analytics",
                "plan_feature_encrypted_sync",
                "plan_feature_data_export"
            ]
        case .coach:
            return ["plan_feature_all_pro", "plan_feature_multi_client", "plan_feature_coach_reports"]
        }
    }
}
