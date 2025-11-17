package bg.tuvarna.devicebackend.integration;

import bg.tuvarna.devicebackend.controllers.exceptions.ErrorResponse;
import bg.tuvarna.devicebackend.models.dtos.AuthResponseDTO;
import bg.tuvarna.devicebackend.models.enums.UserRole;
import bg.tuvarna.devicebackend.services.DeviceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RegistrationLoginIntegrationTests {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper mapper;
    @MockBean
    private DeviceService deviceService;

    private static String token;

    @BeforeEach
    void init() {
        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }


    @Test
    @Order(1)
    void userRegistrationSuccess() throws Exception {
        MvcResult registration1 = mvc.perform(post("/api/v1/users/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "fullName": "Georgi Ivanov",
                          "email": "gosho@abv.bg",
                          "phone": "0884985849",
                          "username": "gosho123",
                          "password": "Az$um_GOSHO123"
                        }""")).andReturn();

        assertEquals(200, registration1.getResponse().getStatus());
    }

    @Test
    @Order(2)
    void userRegistrationFailed() throws Exception {
        MvcResult registration1 = mvc.perform(post("/api/v1/users/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "fullName": "Georgi Ivanov",
                          "email": "gosho@abv.bg",
                          "phone": "0884985849",
                          "username": "gosho123",
                          "password": "Az$um_GOSHO123"
                        }""")).andReturn();
        assertEquals(400, registration1.getResponse().getStatus());

        ErrorResponse errorResponse = mapper.readValue(
                registration1.getResponse().getContentAsString(),
                ErrorResponse.class
        );

        assertEquals("Email already taken", errorResponse.getError());
    }


    @Test
    @Order(3)
    void userLoginSuccess() throws Exception {
        MvcResult login1 = mvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "gosho@abv.bg",
                          "password": "Az$um_GOSHO123"
                        }""")).andReturn();

        assertEquals(200, login1.getResponse().getStatus());

        AuthResponseDTO authResponseDTO = mapper.readValue(login1.getResponse().getContentAsString(), AuthResponseDTO.class);

        token = authResponseDTO.getToken();

        assertNotNull(token);
    }

    @Test
    @Order(4)
    void userLoginFailed() throws Exception {
        mvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "petar123",
                                  "password": "Az$um_PET@R123"
                                }"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Wrong credentials!"));
    }

    @Test
    @Order(5)
    void accessProtectedEndpointWithoutToken() throws Exception {
        mvc.perform(get("/api/v1/users/getUser")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(UserRole.USER.toString()));
    }

    @Test
    @Order(6)
    void userRegistrationEmptyFields() throws Exception {
        mvc.perform(post("/api/v1/users/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "",
                                  "email": "",
                                  "phone": "",
                                  "username": "",
                                  "password": ""
                                }"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(7)
    void userRegistrationInvalidPassword() throws Exception {
        mvc.perform(post("/api/v1/users/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Test User",
                                  "email": "test2@abv.bg",
                                  "phone": "0881234567",
                                  "username": "testuser2",
                                  "password": "short"
                                }"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    void userLoginWrongPassword() throws Exception {
        mvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "gosho@abv.bg",
                                  "password": "WrongPass123"
                                }"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Wrong credentials!"));
    }

    @Test
    @Order(9)
    void accessProtectedEndpointInvalidToken() throws Exception {
        mvc.perform(get("/api/v1/users/getUser")
                        .header("Authorization", "Bearer invalidToken"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(10)
    void userRegistrationDifferentUsernameEmail() throws Exception {
        MvcResult registration = mvc.perform(post("/api/v1/users/registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "New User",
                                  "email": "newuser@abv.bg",
                                  "phone": "0881122334",
                                  "username": "newuser",
                                  "password": "Az$um_NEWUSER123"
                                }"""))
                .andExpect(status().isOk())
                .andReturn();

        assertNotNull(registration.getResponse().getContentAsString());
    }
}
