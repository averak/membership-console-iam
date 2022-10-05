package cc.rits.membership.console.iam.usecase

import org.springframework.beans.factory.annotation.Autowired

/**
 * LoginUseCaseの単体テスト
 */
class LogoutUseCase_UT extends AbstractUseCase_UT {

    @Autowired
    LogoutUseCase sut

    def "handle: ログアウトするとセッションを廃棄する"() {
        when:
        this.sut.handle()

        then:
        noExceptionThrown()
        1 * this.httpSession.invalidate()
    }

}
