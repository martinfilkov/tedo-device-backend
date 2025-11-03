package bg.tuvarna.devicebackend.repositories;

import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.models.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTests {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .fullName("gosho")
                .email("gosho@abv.bg")
                .phone("0888123456")
                .role(UserRole.USER)
                .build();

        userRepository.save(user);

        User user2 = User.builder()
                .fullName("ivan")
                .email("ivan@abv.bg")
                .phone("0888234567")
                .role(UserRole.USER)
                .build();

        userRepository.save(user2);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void userFindBySearchName() {
        User user = userRepository.searchBy("gosho", Pageable.ofSize(1)).getContent().getFirst();
        assertEquals("0888123456", user.getPhone());
    }

    @Test
    void userFindBySearchPhone() {
        User user = userRepository.searchBy("0888123456", Pageable.ofSize(1)).getContent().getFirst();
        assertEquals("gosho", user.getFullName());
    }

    @Test
    void userFindByEmail() {
        User user = userRepository.getByEmail("gosho@abv.bg");
        assertNotNull(user);
        assertEquals("gosho@abv.bg", user.getEmail());
    }

    @Test
    void userFindByPhone() {
        User user = userRepository.getByPhone("0888123456");
        assertNotNull(user);
        assertEquals("gosho", user.getFullName());
    }

    @Test
    void userFindByEmailOrPhone() {
        Optional<User> userByEmail = userRepository.findByEmailOrPhone("gosho@abv.bg");
        Optional<User> userByPhone = userRepository.findByEmailOrPhone("0888123456");

        assertTrue(userByEmail.isPresent());
        assertTrue(userByPhone.isPresent());
        assertEquals("gosho", userByEmail.get().getFullName());
        assertEquals("gosho", userByPhone.get().getFullName());
    }

    @Test
    void searchByEmptyStringReturnsAllUsers() {
        Page<User> users = userRepository.searchBy("", Pageable.ofSize(10));
        assertEquals(2, users.getTotalElements());
    }

    @Test
    void searchByNoMatches() {
        Page<User> users = userRepository.searchBy("nonexistent", Pageable.ofSize(10));
        assertTrue(users.isEmpty());
    }

    @Test
    void getAllUsers() {
        Page<User> users = userRepository.getAllUsers(Pageable.ofSize(10));
        assertEquals(2, users.getTotalElements());
    }

    @Test
    void searchByPhoneNoMatch() {
        Page<User> users = userRepository.searchBy("0888999999", Pageable.ofSize(1));
        assertTrue(users.isEmpty());
    }

    @Test
    void searchByNamePartialMatch() {
        Page<User> users = userRepository.searchBy("gos", Pageable.ofSize(1));
        assertFalse(users.isEmpty());
        assertEquals("gosho", users.getContent().getFirst().getFullName());
    }

    @Test
    void searchByMultipleFields() {
        Page<User> users = userRepository.searchBy("gosho@abv.bg", Pageable.ofSize(1));
        assertFalse(users.isEmpty());
        assertEquals("gosho", users.getContent().getFirst().getFullName());
    }
}
