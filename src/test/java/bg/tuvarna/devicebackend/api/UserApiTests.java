package bg.tuvarna.devicebackend.api;

import bg.tuvarna.devicebackend.controllers.exceptions.ErrorResponse;
import bg.tuvarna.devicebackend.models.dtos.ChangePasswordVO;
import bg.tuvarna.devicebackend.models.dtos.UserCreateVO;
import bg.tuvarna.devicebackend.models.dtos.UserLoginDTO;
import bg.tuvarna.devicebackend.models.dtos.UserUpdateVO;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.models.enums.UserRole;
import bg.tuvarna.devicebackend.repositories.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserApiTests {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        User user = User.builder()
                .fullName("gosho")
                .email("gosho@abv.bg")
                .password(passwordEncoder.encode("Az$um_GOSHO123"))
                .role(UserRole.USER)
                .build();

        userRepository.save(user);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void userRegistrationFailed() throws Exception {
        UserCreateVO userCreateVO = new UserCreateVO(
                "Georgi Ivanov",
                "Az$um_GOSHO123",
                "gosho@abv.bg",
                "0888123456",
                null,
                null,
                null
        );

        MvcResult registration1 = mvc.perform(post("/api/v1/users/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(userCreateVO)))
                .andReturn();
        assertEquals(400, registration1.getResponse().getStatus());

        ErrorResponse errorResponse = mapper.readValue(
                registration1.getResponse().getContentAsString(),
                ErrorResponse.class
        );

        assertEquals("Email already taken", errorResponse.getError());
    }

    @Test
    void userLoginSuccess() throws Exception {
        UserLoginDTO userLoginDTO = new UserLoginDTO(
                "gosho@abv.bg",
                "Az$um_GOSHO123"
        );

        mvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(userLoginDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void userRegistrationSuccess() throws Exception {
        UserCreateVO userCreateVO = new UserCreateVO(
                "Ivan Petrov",
                "Az$um_IVAN123!",
                "ivan.petrov@example.com",
                "0899123456",
                "Sofia",
                null,
                null
        );

        mvc.perform(post("/api/v1/users/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(userCreateVO)))
                .andExpect(status().isOk());
    }

    @Test
    void userRegistrationValidationFails() throws Exception {
        UserCreateVO invalid = new UserCreateVO(
                "",
                "short",
                "not-an-email",
                "",
                null,
                null,
                null
        );

        mvc.perform(post("/api/v1/users/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @WithMockUser(username = "admin", authorities = {"ADMIN"})
    @Test
    void getUsersSuccessWithToken() throws Exception {
        mvc.perform(get("/api/v1/users")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.size").value(5));
    }

    @WithMockUser(username = "admin", authorities = {"ADMIN"})
    @Test
    void updateUserSuccess() throws Exception {
        Long targetId = userRepository.findAll().stream()
                .filter(u -> u.getEmail().equals("gosho@abv.bg"))
                .findFirst().orElseThrow().getId();

        UserUpdateVO update = new UserUpdateVO(
                "Gosho Updated",
                "Updated Address",
                "0888000000",
                "gosho.updated@abv.bg"
        );

        mvc.perform(put("/api/v1/users/{id}", targetId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Gosho Updated"))
                .andExpect(jsonPath("$.email").value("gosho.updated@abv.bg"))
                .andExpect(jsonPath("$.phone").value("0888000000"))
                .andExpect(jsonPath("$.address").value("Updated Address"));
    }

    @Test
    void changePasswordWrongOldFails() throws Exception {
        UserLoginDTO login = new UserLoginDTO("gosho@abv.bg", "Az$um_GOSHO123");
        MvcResult loginResult = mvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        String token = mapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();

        Long id = userRepository.findAll().getFirst().getId();

        ChangePasswordVO change = new ChangePasswordVO(
                "WRONG_OLD",
                "NewPassw0rd!"
        );

        MvcResult res = mvc.perform(put("/api/v1/users/{id}/changePassword", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(mapper.writeValueAsString(change)))
                .andReturn();

        assertEquals(400, res.getResponse().getStatus());
        ErrorResponse er = mapper.readValue(res.getResponse().getContentAsString(), ErrorResponse.class);
        assertEquals("Old password didn't match", er.getError());
    }

    @Test
    void changePasswordSuccessThenLoginWithNewPassword() throws Exception {
        UserLoginDTO login = new UserLoginDTO("gosho@abv.bg", "Az$um_GOSHO123");
        MvcResult loginResult = mvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        String token = mapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();

        Long id = userRepository.findAll().getFirst().getId();

        ChangePasswordVO change = new ChangePasswordVO(
                "Az$um_GOSHO123",
                "N3w_StrongPass!"
        );

        mvc.perform(put("/api/v1/users/{id}/changePassword", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(mapper.writeValueAsString(change)))
                .andExpect(status().isOk());

        UserLoginDTO loginNew = new UserLoginDTO("gosho@abv.bg", "N3w_StrongPass!");
        mvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginNew)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void getUserReturnsPrincipalData() throws Exception {
        UserLoginDTO login = new UserLoginDTO("gosho@abv.bg", "Az$um_GOSHO123");
        MvcResult loginResult = mvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        String token = mapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();

        mvc.perform(get("/api/v1/users/getUser")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("gosho@abv.bg"))
                .andExpect(jsonPath("$.fullName").value("gosho"));
    }

}