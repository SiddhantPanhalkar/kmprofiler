import SwiftUI
import Shared

struct ProfileScreen: View {
    @StateObject private var viewModel = ProfileViewModel()

    var body: some View {
        VStack {
            Text("Profile")
            ActiveSubscriptionCard(state: viewModel.subscriptionState)
        }
    }
}

class ProfileViewModel: ObservableObject {
    @Published var subscriptionState: SubscriptionCardState = .loading
    private let repository: SubscriptionRepository
    private let mapper = CustomerInfoToUiMapper()

    init(repository: SubscriptionRepository) {
        self.repository = repository
    }

    func loadSubscription() {
        repository.getCurrentSubscription { [weak self] result, _ in
            DispatchQueue.main.async {
                switch result {
                case is Subscribed:
                    self?.subscriptionState = .active
                case is NotSubscribed:
                    self?.subscriptionState = .inactive
                case let error as SharedError:
                    self?.subscriptionState = .error(error.errorMessage ?? "Unknown")
                default:
                    break
                }
            }
        }
    }
}

enum SubscriptionCardState {
    case loading
    case active
    case inactive
    case error(String)
}

struct ActiveSubscriptionCard: View {
    let state: SubscriptionCardState

    var body: some View {
        switch state {
        case .active:
            Text("Active")
        case .inactive:
            Text("Inactive")
        case .error(let msg):
            Text(msg)
        case .loading:
            ProgressView()
        }
    }
}
