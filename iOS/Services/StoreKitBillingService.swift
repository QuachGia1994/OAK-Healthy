import Foundation
import Observation
import StoreKit

public struct StoreProductViewState: Equatable, Sendable {
    public let productId: String
    public let displayName: String
    public let description: String
    public let displayPrice: String
}

public enum BillingNotice: Equatable, Sendable {
    case purchaseCompleted
    case purchasePending
    case purchaseCancelled
    case restoreCompleted
    case verificationFailed
    case storeUnavailable
}

@MainActor
@Observable
public final class StoreKitBillingService {
    public private(set) var products: [StoreProductViewState] = []
    public private(set) var isLoading = false
    public private(set) var purchasingProductId: String?
    public private(set) var notice: BillingNotice?

    private let entitlementManager: EntitlementManager
    private var storeProducts: [String: Product] = [:]
    private var transactionTask: Task<Void, Never>?

    public init(entitlementManager: EntitlementManager) {
        self.entitlementManager = entitlementManager
    }

    public func start() async {
        startTransactionListener()
        await refresh()
    }

    public func stop() {
        transactionTask?.cancel()
        transactionTask = nil
    }

    public func refresh() async {
        isLoading = true
        defer { isLoading = false }
        await loadProducts()
        await refreshEntitlements()
    }

    public func purchase(productId: String) async {
        guard let product = storeProducts[productId] else {
            notice = .storeUnavailable
            reportPurchaseResult("store_unavailable", productId: productId)
            return
        }
        purchasingProductId = productId
        defer { purchasingProductId = nil }
        do {
            let result = try await product.purchase()
            await handlePurchaseResult(result, productId: productId)
        } catch {
            notice = .storeUnavailable
            reportPurchaseResult("store_unavailable", productId: productId)
        }
    }

    public func restorePurchases() async {
        do {
            try await AppStore.sync()
            await refreshEntitlements()
            notice = .restoreCompleted
            reportRestoreResult("success")
        } catch {
            notice = .storeUnavailable
            reportRestoreResult("store_unavailable")
        }
    }

    public func clearNotice() {
        notice = nil
    }

    private func loadProducts() async {
        do {
            let ids = CommercialProductCatalog.products.map(\.productId)
            let loaded = try await Product.products(for: ids)
            storeProducts = Dictionary(uniqueKeysWithValues: loaded.map { ($0.id, $0) })
            products = loaded.sorted(by: catalogOrder).map(makeViewState)
            let result = loaded.isEmpty ? "empty" : "success"
            if loaded.isEmpty { notice = .storeUnavailable }
            reportProductsLoaded(result)
        } catch {
            storeProducts = [:]
            products = []
            notice = .storeUnavailable
            reportProductsLoaded("store_unavailable")
        }
    }

    private func catalogOrder(_ lhs: Product, _ rhs: Product) -> Bool {
        let ids = CommercialProductCatalog.products.map(\.productId)
        return (ids.firstIndex(of: lhs.id) ?? .max) < (ids.firstIndex(of: rhs.id) ?? .max)
    }

    private func makeViewState(_ product: Product) -> StoreProductViewState {
        StoreProductViewState(
            productId: product.id,
            displayName: product.displayName,
            description: product.description,
            displayPrice: product.displayPrice
        )
    }

    private func handlePurchaseResult(_ result: Product.PurchaseResult, productId: String) async {
        switch result {
        case .success(let verification):
            guard case .verified(let transaction) = verification else {
                notice = .verificationFailed
                reportPurchaseResult("verification_failed", productId: productId)
                return
            }
            await transaction.finish()
            await refreshEntitlements()
            notice = .purchaseCompleted
            reportPurchaseResult("success", productId: productId)
        case .pending:
            notice = .purchasePending
            reportPurchaseResult("pending", productId: productId)
        case .userCancelled:
            notice = .purchaseCancelled
            reportPurchaseResult("cancelled", productId: productId)
        @unknown default:
            notice = .storeUnavailable
            reportPurchaseResult("store_unavailable", productId: productId)
        }
    }

    private func refreshEntitlements() async {
        var productIds: [String] = []
        for await verification in Transaction.currentEntitlements {
            guard case .verified(let transaction) = verification else { continue }
            guard CommercialProductCatalog.products.contains(where: { $0.productId == transaction.productID }) else { continue }
            productIds.append(transaction.productID)
        }
        entitlementManager.replaceFromStore(CommercialEntitlementResolver.resolve(productIds: productIds))
    }

    private func reportProductsLoaded(_ result: String) {
        DiagnosticsReporter.event(
            "billing_products_loaded",
            fields: CommercialTelemetryFields.result(result, source: "app_store")
        )
    }

    private func reportPurchaseResult(_ result: String, productId: String) {
        DiagnosticsReporter.event(
            "billing_purchase_result",
            fields: CommercialTelemetryFields.result(result, source: "app_store", productId: productId)
        )
    }

    private func reportRestoreResult(_ result: String) {
        DiagnosticsReporter.event(
            "billing_restore_result",
            fields: CommercialTelemetryFields.result(result, source: "app_store")
        )
    }

    private func startTransactionListener() {
        guard transactionTask == nil else { return }
        transactionTask = Task { [weak self] in
            for await verification in Transaction.updates {
                guard !Task.isCancelled else { return }
                await self?.handleTransactionUpdate(verification)
            }
        }
    }

    private func handleTransactionUpdate(_ verification: VerificationResult<Transaction>) async {
        guard case .verified(let transaction) = verification else {
            notice = .verificationFailed
            return
        }
        guard CommercialProductCatalog.products.contains(where: { $0.productId == transaction.productID }) else { return }
        await transaction.finish()
        await refreshEntitlements()
    }
}
