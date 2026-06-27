package br.com.resgateai.enums;

public enum CategoriaProduto {

    LATICINIO("Laticínio", true),
    HORTIFRUTI("Hortifruti", false),
    PADARIA("Padaria", false),
    MERCEARIA("Mercearia", false),
    CARNES("Carnes", true),
    BEBIDAS("Bebidas", false);

    private final String descricao;
    private final boolean refrigerado;

    CategoriaProduto(String descricao, boolean refrigerado) {
        this.descricao = descricao;
        this.refrigerado = refrigerado;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean isRefrigerado() {
        return refrigerado;
    }
}
