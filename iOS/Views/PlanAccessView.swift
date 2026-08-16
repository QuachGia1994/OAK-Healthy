import SwiftUI

public struct PlanAccessView: View {
    @Environment(EntitlementManager.self) private var entitlementManager
    @Environment(StoreKitBillingService.self) private var billingService

    public init() {}

    public var body: some View {
        List {
            currentPlanSection
            planSection(.free)
            planSection(.pro)
            planSection(.coach)
            purchaseSection
        }
        .navigationTitle("plan_access_title".localized)
        .task { await billingService.refresh() }
    }

    private var currentPlanSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 6) {
                Text("plan_access_current_plan".localized)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                Text(planTitle(entitlementManager.snapshot.plan))
                    .font(.title2.weight(.bold))
            }
        }
    }

    private func planSection(_ plan: CommercialPlan) -> some View {
        Section {
            VStack(alignment: .leading, spacing: 10) {
                planHeader(plan)
                ForEach(planFeatureKeys(plan), id: \.self) { key in
                    featureRow(key.localized)
                }
            }
        }
    }

    private func planHeader(_ plan: CommercialPlan) -> some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text(planTitle(plan)).font(.headline)
                Text(planSubtitle(plan)).font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer()
            if entitlementManager.snapshot.plan == plan {
                Text("plan_access_current_badge".localized).font(.caption.weight(.semibold))
            }
        }
    }

    private func featureRow(_ label: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill").foregroundStyle(.tint)
            Text(label).font(.subheadline)
        }
    }

    @ViewBuilder
    private var purchaseSection: some View {
        Section {
            if billingService.isLoading && billingService.products.isEmpty {
                ProgressView()
            }
            ForEach(billingService.products, id: \.productId) { product in
                purchaseRow(product)
            }
            Button("billing_restore".localized) {
                Task { await billingService.restorePurchases() }
            }
            if let notice = billingService.notice {
                Text(noticeText(notice)).font(.footnote).foregroundStyle(.secondary)
            }
        } header: {
            Text("billing_store_products".localized)
        } footer: {
            Text("billing_store_authoritative_note".localized)
        }
    }

    private func purchaseRow(_ product: StoreProductViewState) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text(product.displayName).font(.headline)
                Text(product.displayPrice).font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer()
            Button("billing_buy".localized) {
                Task { await billingService.purchase(productId: product.productId) }
            }
            .buttonStyle(.borderedProminent)
            .disabled(billingService.purchasingProductId != nil)
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
            return ["plan_feature_advanced_cycles", "plan_feature_unlimited_history", "plan_feature_adherence_analytics", "plan_feature_encrypted_sync", "plan_feature_data_export"]
        case .coach:
            return ["plan_feature_all_pro", "plan_feature_multi_client", "plan_feature_coach_reports"]
        }
    }
}
