package bg.tuvarna.devicebackend.services;

import bg.tuvarna.devicebackend.controllers.exceptions.CustomException;
import bg.tuvarna.devicebackend.controllers.exceptions.ErrorCode;
import bg.tuvarna.devicebackend.models.dtos.DeviceCreateVO;
import bg.tuvarna.devicebackend.models.dtos.DeviceUpdateVO;
import bg.tuvarna.devicebackend.models.entities.Device;
import bg.tuvarna.devicebackend.models.entities.Passport;
import bg.tuvarna.devicebackend.models.entities.User;
import bg.tuvarna.devicebackend.repositories.DeviceRepository;
import bg.tuvarna.devicebackend.utils.CustomPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class DeviceServiceTests {

    @MockBean
    private DeviceRepository deviceRepository;

    @MockBean
    private PassportService passportService;

    @Autowired
    private DeviceService deviceService;

    @Test
    void registerDevice_success_returnsSavedDevice_andAddsExtra12MonthsForUser() {
        String serial = "SN-100";
        LocalDate purchase = LocalDate.of(2025, 1, 20);
        User user = User.builder().id(5L).build();

        Passport passport = new Passport();
        passport.setWarrantyMonths(24);

        when(passportService.findPassportBySerialId(serial)).thenReturn(passport);
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        Device saved = deviceService.registerDevice(serial, purchase, user);

        assertNotNull(saved);
        assertEquals(serial, saved.getSerialNumber());
        assertEquals(user, saved.getUser());
        assertEquals(purchase, saved.getPurchaseDate());
        assertEquals(purchase.plusMonths(24).plusMonths(12), saved.getWarrantyExpirationDate());
        verify(deviceRepository).save(any(Device.class));
    }

    @Test
    void registerDevice_invalidSerial_wrapsAsCustomException() {
        when(passportService.findPassportBySerialId("BAD")).thenThrow(new RuntimeException("not found"));

        CustomException ex = assertThrows(CustomException.class,
                () -> deviceService.registerDevice("BAD", LocalDate.now(), User.builder().id(1L).build()));

        assertEquals("Invalid serial number", ex.getMessage());
        assertEquals(ErrorCode.Failed, ex.getErrorCode());
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void findDevice_returnsDeviceOrNull() {
        Device d = new Device(); d.setSerialNumber("SN-200");
        when(deviceRepository.findById("SN-200")).thenReturn(Optional.of(d));
        when(deviceRepository.findById("MISSING")).thenReturn(Optional.empty());

        assertSame(d, deviceService.findDevice("SN-200"));
        assertNull(deviceService.findDevice("MISSING"));
    }

    @Test
    void isDeviceExists_returnsDevice_whenExists() {
        Device d = new Device(); d.setSerialNumber("SN-201");
        when(deviceRepository.existsById("SN-201")).thenReturn(true);
        when(deviceRepository.findById("SN-201")).thenReturn(Optional.of(d));

        assertSame(d, deviceService.isDeviceExists("SN-201"));
    }

    @Test
    void isDeviceExists_throwsWhenMissing() {
        when(deviceRepository.existsById("NOPE")).thenReturn(false);

        CustomException ex = assertThrows(CustomException.class, () -> deviceService.isDeviceExists("NOPE"));
        assertEquals("Device not registered", ex.getMessage());
        assertEquals(ErrorCode.NotRegistered, ex.getErrorCode());
    }

    @Test
    void registerNewDevice_success_returnsSavedDevice() {
        String serial = "SN-300";
        LocalDate purchase = LocalDate.of(2025, 5, 5);
        User user = User.builder().id(9L).build();

        DeviceCreateVO vo = new DeviceCreateVO(serial, purchase);

        // alreadyExist passes
        when(deviceRepository.findById(serial)).thenReturn(Optional.empty());

        Passport passport = new Passport();
        passport.setWarrantyMonths(18);
        when(passportService.findPassportBySerialId(serial)).thenReturn(passport);
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        Device saved = deviceService.registerNewDevice(vo, user);

        assertNotNull(saved);
        assertEquals(serial, saved.getSerialNumber());
        assertEquals(user, saved.getUser());
        assertEquals(purchase.plusMonths(18).plusMonths(12), saved.getWarrantyExpirationDate());
    }

    @Test
    void registerNewDevice_nullUser_throwsEntityNotFound() {
        DeviceCreateVO vo = new DeviceCreateVO("SN-301", LocalDate.now());
        when(deviceRepository.findById("SN-301")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> deviceService.registerNewDevice(vo, null));
        assertEquals("User not found", ex.getMessage());
        assertEquals(ErrorCode.EntityNotFound, ex.getErrorCode());
        verify(passportService, never()).findPassportBySerialId(anyString());
    }

    @Test
    void alreadyExist_throwsIfFound() {
        when(deviceRepository.findById("SN-400")).thenReturn(Optional.of(new Device()));
        CustomException ex = assertThrows(CustomException.class, () -> deviceService.alreadyExist("SN-400"));
        assertEquals("Device already registered", ex.getMessage());
        assertEquals(ErrorCode.AlreadyExists, ex.getErrorCode());
    }

    @Test
    void updateDevice_recomputesWarranty_withoutUser_noExtra12Months() {
        String serial = "SN-500";
        LocalDate newPurchase = LocalDate.of(2025, 2, 1);

        Passport passport = new Passport();
        passport.setWarrantyMonths(12);

        Device existing = new Device();
        existing.setSerialNumber(serial);
        existing.setUser(null);
        existing.setPassport(passport);

        when(deviceRepository.findById(serial)).thenReturn(Optional.of(existing));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        Device updated = deviceService.updateDevice(serial, new DeviceUpdateVO(newPurchase, serial));

        assertEquals(newPurchase, updated.getPurchaseDate());
        assertEquals(newPurchase.plusMonths(12), updated.getWarrantyExpirationDate());
    }

    @Test
    void updateDevice_recomputesWarranty_withUser_addsExtra12Months() {
        String serial = "SN-501";
        LocalDate newPurchase = LocalDate.of(2025, 3, 10);

        Passport passport = new Passport();
        passport.setWarrantyMonths(36);

        Device existing = new Device();
        existing.setSerialNumber(serial);
        existing.setUser(User.builder().id(1L).build()); // user present
        existing.setPassport(passport);

        when(deviceRepository.findById(serial)).thenReturn(Optional.of(existing));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        Device updated = deviceService.updateDevice(serial, new DeviceUpdateVO(newPurchase, serial));

        assertEquals(newPurchase.plusMonths(36).plusMonths(12), updated.getWarrantyExpirationDate());
        assertEquals(serial, updated.getComment());
    }

    @Test
    void updateDevice_notFound_throwsEntityNotFound() {
        when(deviceRepository.findById("MIA")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> deviceService.updateDevice("MIA", new DeviceUpdateVO(LocalDate.now(), "x")));
        assertEquals("Device not found", ex.getMessage());
        assertEquals(ErrorCode.EntityNotFound, ex.getErrorCode());
    }

    @Test
    void deleteDevice_success_invokesRepository() {
        doNothing().when(deviceRepository).deleteBySerialNumber("SN-600");
        deviceService.deleteDevice("SN-600");
        verify(deviceRepository).deleteBySerialNumber("SN-600");
    }

    @Test
    void deleteDevice_runtimeFailure_wrapsWithNewMessage() {
        doThrow(new RuntimeException("fk")).when(deviceRepository).deleteBySerialNumber("BAD");
        CustomException ex = assertThrows(CustomException.class, () -> deviceService.deleteDevice("BAD"));
        assertEquals("Cannot delete device: renovations exist", ex.getMessage());
        assertEquals(ErrorCode.Failed, ex.getErrorCode());
    }

    @Test
    void addAnonymousDevice_success_returnsSaved_andNoExtra12Months() {
        String serial = "SN-700";
        LocalDate purchase = LocalDate.of(2024, 12, 15);

        DeviceCreateVO vo = new DeviceCreateVO(serial, purchase);

        when(deviceRepository.findById(serial)).thenReturn(Optional.empty()); // alreadyExist passes
        Passport passport = new Passport();
        passport.setWarrantyMonths(6);
        when(passportService.findPassportBySerialId(serial)).thenReturn(passport);
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        Device saved = deviceService.addAnonymousDevice(vo);

        assertNull(saved.getUser());
        assertEquals(purchase.plusMonths(6), saved.getWarrantyExpirationDate());
        assertEquals(serial, saved.getSerialNumber());
    }

    @Test
    void addAnonymousDevice_invalidSerial_throwsCustomException() {
        String serial = "X";
        DeviceCreateVO vo = new DeviceCreateVO(serial, LocalDate.now());

        when(deviceRepository.findById(serial)).thenReturn(Optional.empty());
        when(passportService.findPassportBySerialId(serial)).thenThrow(new RuntimeException("no passport"));

        CustomException ex = assertThrows(CustomException.class, () -> deviceService.addAnonymousDevice(vo));
        assertEquals("Invalid serial number", ex.getMessage());
        assertEquals(ErrorCode.Failed, ex.getErrorCode());
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void addAnonymousDevice_alreadyExists_throwsAlreadyExists() {
        String serial = "SN-701";
        DeviceCreateVO vo = new DeviceCreateVO(serial, LocalDate.now());

        when(deviceRepository.findById(serial)).thenReturn(Optional.of(new Device()));

        CustomException ex = assertThrows(CustomException.class, () -> deviceService.addAnonymousDevice(vo));
        assertEquals("Device already registered", ex.getMessage());
        assertEquals(ErrorCode.AlreadyExists, ex.getErrorCode());
    }

    @Test
    void getDevices_noSearch_returnsPagedContent() {
        Device d1 = new Device(); d1.setSerialNumber("A");
        Device d2 = new Device(); d2.setSerialNumber("B");

        Page<Device> page = new PageImpl<>(List.of(d1, d2), PageRequest.of(0, 2), 4);
        when(deviceRepository.getAllDevices(PageRequest.of(0, 2))).thenReturn(page);

        CustomPage<Device> result = deviceService.getDevices(null, 1, 2);

        assertEquals(2, result.getItems().size());
        assertEquals(4, result.getTotalItems());
        assertEquals(2, result.getTotalPages());
        assertEquals(1, result.getCurrentPage());
        assertEquals(2, result.getSize());
    }

    @Test
    void getDevices_withSearch_usesRepositoryFinder() {
        Device d = new Device(); d.setSerialNumber("SN-SEARCH");

        Page<Device> page = new PageImpl<>(List.of(d), PageRequest.of(0, 2), 1);
        when(deviceRepository.findAll("SN", PageRequest.of(0, 2))).thenReturn(page);

        CustomPage<Device> result = deviceService.getDevices("SN", 1, 2);

        assertEquals(1, result.getItems().size());
        assertEquals("SN-SEARCH", result.getItems().getFirst().getSerialNumber());
        assertEquals(1, result.getTotalItems());
        assertEquals(1, result.getTotalPages());
    }
}
