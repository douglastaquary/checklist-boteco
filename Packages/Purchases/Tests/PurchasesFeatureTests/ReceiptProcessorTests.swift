import XCTest
@testable import PurchasesFeature

final class ReceiptProcessorTests: XCTestCase {
  private let fixture = """
    OMERC COMPRE MELHOR DE GENEROS ALIMENTIC. LTDA
    CNPJ 12.345.678/0001-90
    Data: 15/06/2026

    DESCRICAO QTD VL. UNIT TOTAL
    CERVEJA HEINEKEN LN 330ML 24 4,50 108,00
    AGUA COM GAS 510ML 12 1,80 21,60
    DETERGENTE NEUTRO 500ML 6 3,49 20,94
    CARNE BOVINA PATINHO KG 5 42,90 214,50
    COPO DESCARTAVEL 200ML 10 8,90 89,00
    PAPEL HIGIENICO FOLHA DUPLA 4 12,50 50,00
    REFRIGERANTE COCA COLA 2L 8 7,99 63,92

    QTD. TOTAL DE ITENS 69
    VALOR TOTAL (R$) 567,96
    FORMA DE PAGAMENTO
    TER CARTAO DEBITO
    """

  func testParseFixture() {
    let scan = ReceiptProcessor.parseReceipt(fixture)
    XCTAssertGreaterThanOrEqual(scan.items.count, 6)
    XCTAssertEqual(scan.paymentMethod, "Cartão Débito")
    XCTAssertEqual(scan.totalInCents, 56796)
    XCTAssertEqual(scan.purchaseDate, "2026-06-15")
  }

  func testGroupsSortedBySpend() {
    let scan = ReceiptProcessor.parseReceipt(fixture)
    let session = ReceiptProcessor.merge(session: ReceiptSession(), scan: scan)
    let groups = ReceiptProcessor.buildGroups(session: session)
    XCTAssertFalse(groups.isEmpty)
    XCTAssertEqual(groups.map(\.subtotalInCents), groups.map(\.subtotalInCents).sorted(by: >))
    XCTAssertTrue(groups.contains(where: \.isTopSpend))
  }
}
