//
//  AztectFlutterContainer.swift
//  Pods
//
//  Created by Sawant, Prashant on 3/3/25.
//

final class AztecFlutterContainer {
    static let shared = AztecFlutterContainer()  // The single shared instance
    
    private init() {}
    
    var flutterApi: AztecFlutterApiProtocol?
}
