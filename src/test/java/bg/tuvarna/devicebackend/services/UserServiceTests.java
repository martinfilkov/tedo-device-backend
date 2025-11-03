package bg.tuvarna.devicebackend.services;

import bg.tuvarna.devicebackend.controllers.exceptions.CustomException;
import bg.tuvarna.devicebackend.models.dtos.UserCreateVO;
import bg.tuvarna.devicebackend.models.dtos.UserListing;
import bg.tuvarna.devicebackend.models.dtos.UserUpdateVO;
import bg.tuvarna.devicebackend.models.entities.Device;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.models.enums.UserRole;
import bg.tuvarna.devicebackend.repositories.UserRepository;
import bg.tuvarna.devicebackend.utils.CustomPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class UserServiceTests {
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private PasswordEncoder passwordEncoder;
    @MockBean
    private DeviceService deviceService;
    @Autowired
    private UserService userService;

    @Test
    void testUserService() {
        UserCreateVO userCreateVO = new UserCreateVO(
                "Ivan",
                "123",
                "Email",
                "+123",
                "address",
                LocalDate.now(),
                "123451"
        );

        when(userRepository.getByPhone("+123")).thenReturn(new User());
        CustomException ex = assertThrows(
                CustomException.class,
                () -> userService.register(userCreateVO)
        );
        assertEquals("Phone already taken", ex.getMessage());
    }


    @Test
    void testRegistrationSuccessful() {
        UserCreateVO userCreateVO = new UserCreateVO(
                "Ivan",
                "123",
                "Email",
                "+123",
                "address",
                LocalDate.now(),
                "123451"
        );

        doNothing().when(deviceService).alreadyExist(any());
        when(deviceService.registerDevice(any(), any(), any()))
                .thenReturn(new Device());
        when(userRepository.save(any()))
                .thenReturn(User.builder()
                        .id(1L)
                        .phone("+123")
                        .email("Email")
                        .fullName("Ivan")
                        .address("address")
                        .password("123")
                        .role(UserRole.USER)
                        .devices(List.of())
                        .build());

        userService.register(userCreateVO);

        verify(deviceService, times(1))
                .alreadyExist(any());
        verify(deviceService, times(1))
                .registerDevice(any(), any(), any());
    }

    @Test
    void testUserRegistrationWithExistingEmail() {
        UserCreateVO userCreateVO = new UserCreateVO(
                "Ivan",
                "123",
                "existingemail@example.com",
                "+123",
                "address",
                LocalDate.now(),
                "123451"
        );

        when(userRepository.getByEmail("existingemail@example.com")).thenReturn(new User());

        CustomException ex = assertThrows(
                CustomException.class,
                () -> userService.register(userCreateVO)
        );
        assertEquals("Email already taken", ex.getMessage());
    }

    @Test
    void testUserUpdateSuccessful() {
        UserUpdateVO userUpdateVO = new UserUpdateVO(
                "Updated Name",
                "Updated Address",
                "+456",
                "updatedemail@example.com"
        );

        User existingUser = User.builder()
                .id(1L)
                .phone("+123")
                .email("oldemail@example.com")
                .fullName("Old Name")
                .address("Old Address")
                .password("oldpassword")
                .role(UserRole.USER)
                .build();

        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        userService.updateUser(1L, userUpdateVO);

        assertEquals("Updated Name", existingUser.getFullName());
        assertEquals("updatedemail@example.com", existingUser.getEmail());
        assertEquals("+456", existingUser.getPhone());
        assertEquals("Updated Address", existingUser.getAddress());
    }

    @Test
    void testUserUpdateWithExistingPhone() {
        UserUpdateVO userUpdateVO = new UserUpdateVO(
                "Updated Name",
                "newemail@example.com",
                "+789",
                "Updated Address"
        );

        User existingUser = User.builder()
                .id(1L)
                .phone("+123")
                .email("oldemail@example.com")
                .fullName("Old Name")
                .address("Old Address")
                .password("oldpassword")
                .role(UserRole.USER)
                .build();

        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(existingUser));
        when(userRepository.getByPhone("+789")).thenReturn(new User());

        CustomException ex = assertThrows(
                CustomException.class,
                () -> userService.updateUser(1L, userUpdateVO)
        );
        assertEquals("Phone already taken", ex.getMessage());
    }

    @Test
    void testGetUserByIdNotFound() {
        when(userRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        CustomException ex = assertThrows(
                CustomException.class,
                () -> userService.getUserById(999L)
        );
        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void testGetUsersWithSearchQuery() {
        User user1 = new User(1L, "John", "john@example.com", "+111", "Address 1", "password1", UserRole.USER, List.of());

        Page<User> userPage = new PageImpl<>(List.of(user1), PageRequest.of(0, 2), 2);
        when(userRepository.searchBy("john", PageRequest.of(0, 2))).thenReturn(userPage);

        CustomPage<UserListing> result = userService.getUsers("john", 1, 2);

        assertEquals(1, result.getItems().size());
        assertEquals("John", result.getItems().getFirst().fullName());
    }

}