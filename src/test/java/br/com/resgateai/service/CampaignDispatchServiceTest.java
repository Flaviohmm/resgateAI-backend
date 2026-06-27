package br.com.resgateai.service;

import br.com.resgateai.dto.response.DispatchResponse;
import br.com.resgateai.entity.Campanha;
import br.com.resgateai.entity.Lote;
import br.com.resgateai.entity.Recomendacao;
import br.com.resgateai.repository.CampanhaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CampaignDispatchServiceTest {

    @Mock
    private CampanhaRepository campanhaRepository;
    @Mock
    private TelegramClient telegramClient;
    @Mock
    private SustainabilityService sustainabilityService;

    private CampaignDispatchService service() {
        return new CampaignDispatchService(campanhaRepository, telegramClient, sustainabilityService);
    }

    private Campanha campanhaNaoEnviada() {
        Lote lote = Lote.builder().id(1L).quantidade(20).build();
        Recomendacao recomendacao = Recomendacao.builder().id(50L).lote(lote).build();
        return Campanha.builder()
                .id(99L)
                .recomendacao(recomendacao)
                .canalWhatsapp("Promo de iogurte!")
                .canalStory("Corre!")
                .canalEtiqueta("Iogurte -30%")
                .build();
    }

    @Test
    void enviaPelaPrimeiraVezEPersisteEnviadaEm() {
        Campanha campanha = campanhaNaoEnviada();
        when(campanhaRepository.findById(99L)).thenReturn(Optional.of(campanha));
        when(campanhaRepository.save(any(Campanha.class))).thenAnswer(inv -> inv.getArgument(0));

        DispatchResponse resp = service().enviar(99L);

        verify(telegramClient, times(1)).sendMessage(anyString());
        verify(sustainabilityService, times(1)).registrarImpacto(campanha.getRecomendacao());
        assertThat(resp.getStatus()).isEqualTo("enviada");
        assertThat(resp.getEnviadaEm()).isNotNull();
    }

    @Test
    void naoReenviaSeJaEnviadaEDevolveHorarioOriginal() {
        Campanha campanha = campanhaNaoEnviada();
        LocalDateTime original = LocalDateTime.now().minusHours(1);
        campanha.setEnviadaEm(original);
        when(campanhaRepository.findById(99L)).thenReturn(Optional.of(campanha));

        DispatchResponse resp = service().enviar(99L);

        verify(telegramClient, never()).sendMessage(anyString());
        verify(sustainabilityService, never()).registrarImpacto(any());
        assertThat(resp.getEnviadaEm()).isEqualTo(original);
    }

    @Test
    void lancaNotFoundQuandoCampanhaNaoExiste() {
        when(campanhaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().enviar(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
