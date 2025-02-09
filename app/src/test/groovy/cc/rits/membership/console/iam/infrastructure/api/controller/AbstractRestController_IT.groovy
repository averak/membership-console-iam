package cc.rits.membership.console.iam.infrastructure.api.controller

import cc.rits.membership.console.iam.AbstractDatabaseSpecification
import cc.rits.membership.console.iam.config.auth.LoginUserDetails
import cc.rits.membership.console.iam.domain.model.ClientModel
import cc.rits.membership.console.iam.domain.model.UserModel
import cc.rits.membership.console.iam.enums.Scope
import cc.rits.membership.console.iam.exception.BaseException
import cc.rits.membership.console.iam.helper.JsonConvertHelper
import cc.rits.membership.console.iam.helper.RandomHelper
import cc.rits.membership.console.iam.infrastructure.api.response.AccessTokenResponse
import cc.rits.membership.console.iam.infrastructure.api.response.ErrorResponse
import cc.rits.membership.console.iam.property.AuthProperty
import cc.rits.membership.console.iam.util.AuthUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.util.MultiValueMap
import org.springframework.web.context.WebApplicationContext
import spock.lang.Shared

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity

/**
 * RestController統合テストの基底クラス
 */
abstract class AbstractRestController_IT extends AbstractDatabaseSpecification {

    private MockMvc mockMvc

    @Autowired
    private WebApplicationContext webApplicationContext

    @Autowired
    private PlatformTransactionManager transactionManager

    @Autowired
    private MessageSource messageSource

    @Autowired
    protected AuthUtil authUtil

    @Autowired
    protected AuthProperty authProperty

    @Autowired
    private JdbcRegisteredClientRepository registeredClientRepository

    @Shared
    protected MockHttpSession session = new MockHttpSession()

    @Shared
    protected Authentication authentication = null

    /**
     * ログインユーザのID
     */
    static final Integer LOGIN_USER_ID = 1

    /**
     * ログインユーザのメールアドレス
     */
    static final String LOGIN_USER_EMAIL = RandomHelper.email()

    /**
     * GET request
     *
     * @param path path
     *
     * @return HTTP request builder
     */
    MockHttpServletRequestBuilder getRequest(final String path) {
        return MockMvcRequestBuilders.get(path)
            .session(this.session)
            .with(authentication(this.authentication))
    }

    /**
     * POST request
     *
     * @param path path
     *
     * @return HTTP request builder
     */
    MockHttpServletRequestBuilder postRequest(final String path) {
        return MockMvcRequestBuilders.post(path)
            .session(this.session)
            .with(authentication(this.authentication))
    }

    /**
     * POST request (Form)
     *
     * @param path path
     * @param params query params
     *
     * @return HTTP request builder
     */
    MockHttpServletRequestBuilder postRequest(final String path, final MultiValueMap<String, String> params) {
        return MockMvcRequestBuilders.post(path)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .params(params)
            .session(this.session)
            .with(authentication(this.authentication))
    }

    /**
     * POST request (JSON)
     *
     * @param path path
     * @param content request body
     *
     * @return HTTP request builder
     */
    MockHttpServletRequestBuilder postRequest(final String path, final Object content) {
        return MockMvcRequestBuilders.post(path)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .content(JsonConvertHelper.convertObjectToJson(content))
            .session(this.session)
            .with(authentication(this.authentication))
    }

    /**
     * PUT request (JSON)
     *
     * @param path path
     * @param content request body
     *
     * @return HTTP request builder
     */
    MockHttpServletRequestBuilder putRequest(final String path, final Object content) {
        return MockMvcRequestBuilders.put(path)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .content(JsonConvertHelper.convertObjectToJson(content))
            .session(this.session)
            .with(authentication(this.authentication))
    }

    /**
     * DELETE request
     *
     * @param path path
     *
     * @return HTTP request builder
     */
    MockHttpServletRequestBuilder deleteRequest(final String path) {
        return MockMvcRequestBuilders.delete(path)
            .session(this.session)
            .with(authentication(this.authentication))
    }

    /**
     * Execute request
     *
     * @param request HTTP request builder
     * @param status expected HTTP status
     *
     * @return MVC result
     */
    MvcResult execute(final MockHttpServletRequestBuilder request, final HttpStatus status) {
        final result = this.mockMvc.perform(request).andReturn()

        assert result.response.status == status.value()
        return result
    }

    /**
     * Execute request / return response
     *
     * @param request HTTP request builder
     * @param status expected HTTP status
     * @param clazz response class
     *
     * @return response
     */
    def <T> T execute(final MockHttpServletRequestBuilder request, final HttpStatus status, final Class<T> clazz) {
        final result = this.mockMvc.perform(request).andReturn()

        assert result.response.status == status.value()
        return JsonConvertHelper.convertJsonToObject(result.getResponse().getContentAsString(), clazz)
    }

    /**
     * Execute request / verify exception
     *
     * @param request HTTP request builder
     * @param exception expected exception
     *
     * @return error response
     */
    ErrorResponse execute(final MockHttpServletRequestBuilder request, final BaseException exception) {
        final result = this.mockMvc.perform(request).andReturn()
        final response = JsonConvertHelper.convertJsonToObject(result.response.contentAsString, ErrorResponse.class)

        final expectedErrorMessage = this.getErrorMessage(exception)

        assert result.response.status == exception.httpStatus.value()
        assert result.response.status == exception.errorCode.httpStatus.value()
        assert response.code == exception.errorCode.code
        assert response.message == expectedErrorMessage
        return response
    }

    /**
     * エラーメッセージを取得
     *
     * @param exception exception
     * @return エラーメッセージ
     */
    private String getErrorMessage(final BaseException exception) {
        final messageKey = exception.errorCode.messageKey
        final args = exception.args
        return this.messageSource.getMessage(messageKey, args, Locale.ENGLISH)
    }

    /**
     * ログイン
     *
     * @return ログインユーザ
     */
    protected UserModel login() {
        final user = UserModel.builder()
            .id(LOGIN_USER_ID)
            .firstName(RandomHelper.alphanumeric(10))
            .lastName(RandomHelper.alphanumeric(10))
            .email(LOGIN_USER_EMAIL)
            .password(RandomHelper.password())
            .entranceYear(2000)
            .build()

        sql.dataSet("user").add(
            id: user.id,
            first_name: user.firstName,
            last_name: user.lastName,
            email: user.email,
            password: this.authUtil.hashingPassword(user.password),
            entrance_year: user.entranceYear,
        )

        final authorities = AuthorityUtils.createAuthorityList("ROLE_USER")
        final userDetails = new LoginUserDetails(user, authorities)
        this.authentication = new UsernamePasswordAuthenticationToken(userDetails, user.password, userDetails.authorities)

        return user
    }

    /**
     * ログアウト
     */
    protected void logout() {
        if (!this.session.invalid) {
            this.session.invalidate()
        }
        this.authentication = null
    }

    /**
     * クライアントを作成
     *
     * @return アクセストークン
     */
    protected AccessTokenResponse createClient(final List<Scope> scopes) {
        final client = ClientModel.builder()
            .name(RandomHelper.alphanumeric(200))
            .scopes(scopes)
            .build()

        final registeredClientBuilder = RegisteredClient.withId(client.getId())
        registeredClientBuilder.clientName(client.name)
        registeredClientBuilder.clientId(client.clientId)
        registeredClientBuilder.clientSecret(this.authUtil.hashingPassword(client.clientSecret))
        registeredClientBuilder.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        client.getScopes().forEach(scope -> registeredClientBuilder.scope(scope.name))
        final registeredClient = registeredClientBuilder.build()
        this.registeredClientRepository.save(registeredClient)

        final request = MockMvcRequestBuilders.post("/oauth2/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getUrlEncoder().encodeToString("${client.clientId}:${client.clientSecret}".bytes))
            .param("grant_type", "client_credentials")
        return this.execute(request, HttpStatus.OK, AccessTokenResponse)
    }

    /**
     * setup before test case
     */
    def setup() {
        this.mockMvc = MockMvcBuilders
            .webAppContextSetup(this.webApplicationContext)
            .addFilter(({ request, response, chain ->
                response.setCharacterEncoding("UTF-8")
                chain.doFilter(request, response)
            }))
            .apply(springSecurity())
            .build()
    }

    /**
     * cleanup after test case
     */
    def cleanup() {
        this.logout()
    }

}
