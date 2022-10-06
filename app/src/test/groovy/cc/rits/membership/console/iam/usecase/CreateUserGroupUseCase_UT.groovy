package cc.rits.membership.console.iam.usecase

import cc.rits.membership.console.iam.domain.model.UserModel
import cc.rits.membership.console.iam.enums.Role
import cc.rits.membership.console.iam.exception.BaseException
import cc.rits.membership.console.iam.exception.ErrorCode
import cc.rits.membership.console.iam.exception.ForbiddenException
import cc.rits.membership.console.iam.helper.RandomHelper
import cc.rits.membership.console.iam.infrastructure.api.request.UserGroupUpsertRequest
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared

/**
 * CreateUserGroupUseCaseの単体テスト
 */
class CreateUserGroupUseCase_UT extends AbstractUseCase_UT {

    @Autowired
    CreateUserGroupUseCase sut

    @Shared
    UserGroupUpsertRequest requestBody = RandomHelper.mock(UserGroupUpsertRequest)

    def setup() {
        this.requestBody.roles = [Role.IAM_VIEWER.id, Role.IAM_ADMIN.id]
    }

    def "handle: IAMの管理者がユーザグループを作成"() {
        given:
        final loginUser = Spy(UserModel)
        final requestBody = RandomHelper.mock(UserGroupUpsertRequest)

        when:
        this.sut.handle(loginUser, requestBody)

        then:
        1 * loginUser.hasRole(Role.IAM_ADMIN) >> true
        1 * this.userGroupService.checkIsNameAlreadyUsed(requestBody.name) >> {}
        1 * this.userGroupRepository.insert(_)
    }

    def "handle: IAMの管理者以外はユーザグループを作成不可"() {
        given:
        final loginUser = Spy(UserModel)
        final requestBody = RandomHelper.mock(UserGroupUpsertRequest)

        when:
        this.sut.handle(loginUser, requestBody)

        then:
        1 * loginUser.hasRole(Role.IAM_ADMIN) >> false
        final BaseException exception = thrown()
        verifyException(exception, new ForbiddenException(ErrorCode.USER_HAS_NO_PERMISSION))
    }

}
