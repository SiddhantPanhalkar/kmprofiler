import Shared
import SwiftUI

struct ContentView: View {
    let repo = UserRepository()
    var body: some View {
        Text(repo.getUser(id: "42"))
    }
}
